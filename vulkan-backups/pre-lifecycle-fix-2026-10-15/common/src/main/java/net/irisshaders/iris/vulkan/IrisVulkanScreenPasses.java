package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.shaderpack.loading.ProgramArrayId;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Milestone 1 of the Vulkan renderer: a shader pack's begin/prepare/deferred/composite/final passes, run on top of
 * the frame vanilla rendered. There are no gbuffer or shadow programs yet, so {@code colortex0} receives the vanilla
 * scene (like a pack without gbuffer programs on OpenGL) and depth textures hold vanilla's world depth.
 *
 * <p>Render target handling follows Iris's OpenGL {@code CompositeRenderer}: one buffer flipper shared across the
 * stages, a pass reads {@code alt} for flipped buffers and {@code main} otherwise and writes the opposite, written
 * buffers flip afterwards unless {@code flip.<pass>.<buffer>=false}, and {@code <stage>_pre} flips apply first.</p>
 */
final class IrisVulkanScreenPasses implements AutoCloseable {
	private static final ProgramArrayId[] EARLY_STAGES = {ProgramArrayId.Begin, ProgramArrayId.Prepare};
	private static final ProgramArrayId[] LATE_STAGES = {ProgramArrayId.Deferred, ProgramArrayId.Composite};
	private static final Set<String> DEPTH_SAMPLERS = Set.of("depthtex0", "gdepthtex", "depthtex1", "depthtex2");
	private static final int QUAD_VERTEX_SIZE = 20;

	private record Pass(String name, IrisVulkanProgram program, int[] drawBuffers, Set<Integer> flipped) {
	}

	private final String packKey;
	private final FrameUpdateNotifier updateNotifier = new FrameUpdateNotifier();
	private final IrisVulkanUniforms uniforms;
	private final Map<Integer, IrisVulkanTarget> targets = new HashMap<>();
	private final Map<Integer, Vector4f> clearColors = new HashMap<>();
	private final List<Pass> earlyPasses = new ArrayList<>();
	private final List<Pass> latePasses = new ArrayList<>();
	private final Set<Integer> flippedForScene;
	private final Pass finalPass;
	private final Set<Integer> flippedForFinal;
	private final Set<String> warned = new HashSet<>();
	private final IrisVulkanBlit blit = new IrisVulkanBlit();
	private final GpuBuffer quad;
	private final GpuTexture noise;
	private final GpuTextureView noiseView;
	private final GpuTexture placeholder;
	private final GpuTextureView placeholderView;
	private GpuTexture worldDepth;
	private GpuTextureView worldDepthView;
	private int width;
	private int height;

	IrisVulkanScreenPasses(String packKey, ProgramSet programSet, int width, int height) {
		this.packKey = packKey;
		this.width = width;
		this.height = height;
		PackDirectives directives = programSet.getPackDirectives();
		this.uniforms = IrisVulkanUniforms.forScreenPasses(programSet.getPack().getIdMap(), directives, updateNotifier);
		this.quad = createQuad();
		int noiseSize = directives.getNoiseTextureResolution();
		this.noise = createNoise(noiseSize);
		this.noiseView = RenderSystem.getDevice().createTextureView(noise);
		this.placeholder = RenderSystem.getDevice().createTexture("iris vulkan placeholder", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
		this.placeholderView = RenderSystem.getDevice().createTextureView(placeholder);
		RenderSystem.getDevice().createCommandEncoder().clearColorTexture(placeholder, new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));

		try {
			getOrCreateTarget(directives, 0);
			Set<Integer> flipped = new LinkedHashSet<>();
			for (ProgramArrayId stage : EARLY_STAGES) {
				planStage(programSet, directives, stage, flipped, earlyPasses);
			}
			this.flippedForScene = Set.copyOf(flipped);
			for (ProgramArrayId stage : LATE_STAGES) {
				planStage(programSet, directives, stage, flipped, latePasses);
			}
			this.flippedForFinal = Set.copyOf(flipped);
			this.finalPass = programSet.get(ProgramId.Final)
				.filter(ProgramSource::isValid)
				.map(source -> compile(directives, source, TextureStage.COMPOSITE_AND_FINAL, new int[]{0}, flippedForFinal, true))
				.orElse(null);
		} catch (RuntimeException e) {
			close();
			throw e;
		}

		directives.getRenderTargetDirectives().getRenderTargetSettings().forEach((index, settings) -> {
			if (targets.containsKey(index) && settings.shouldClear()) {
				clearColors.put(index, settings.getClearColor().orElse(defaultClearColor(index)));
			}
		});

		Iris.logger.info("[Iris/Vulkan] Built screen passes for {}: {} early, {} deferred/composite, final={}, targets={}, {} supplied uniforms.",
			packKey, earlyPasses.size(), latePasses.size(), finalPass != null, targets.keySet(), uniforms.suppliedTypes().size());
	}

	private static Vector4f defaultClearColor(int index) {
		// Iris's OpenGL defaults (ClearPassCreator); colortex0 is overwritten with the scene each frame anyway.
		return index == 1 ? new Vector4f(1.0f, 1.0f, 1.0f, 1.0f) : new Vector4f(0.0f, 0.0f, 0.0f, index == 0 ? 1.0f : 0.0f);
	}

	private void planStage(ProgramSet programSet, PackDirectives directives, ProgramArrayId stage, Set<Integer> flipped, List<Pass> out) {
		String stageName = stage.name().toLowerCase(java.util.Locale.ROOT);
		directives.getExplicitFlips(stageName + "_pre").forEach((buffer, flip) -> {
			if (flip) {
				toggle(flipped, buffer);
			}
		});

		TextureStage textureStage = textureStage(stage);

		if (programSet.getCompute(stage).length > 0 && java.util.Arrays.stream(programSet.getCompute(stage)).anyMatch(c -> c != null && c.length > 0)) {
			warnOnce(stageName + ":compute", "Pack declares {} compute shaders; RenderPearl has no compute stage, they are skipped.", stageName);
		}

		for (ProgramSource source : programSet.getComposite(stage)) {
			if (source == null || !source.isValid()) {
				continue;
			}

			ProgramDirectives programDirectives = source.getDirectives();
			int[] drawBuffers = programDirectives.getDrawBuffers();
			Pass pass = compile(directives, source, textureStage, drawBuffers, Set.copyOf(flipped), false);
			out.add(pass);

			var explicitFlips = programDirectives.getExplicitFlips();
			for (int buffer : drawBuffers) {
				if (explicitFlips.get(buffer) != Boolean.FALSE) {
					toggle(flipped, buffer);
				}
			}
			explicitFlips.forEach((buffer, flip) -> {
				if (flip) {
					toggle(flipped, buffer);
				}
			});
		}
	}

	static TextureStage textureStage(ProgramArrayId stage) {
		return switch (stage) {
			case Begin -> TextureStage.BEGIN;
			case Prepare -> TextureStage.PREPARE;
			case Deferred -> TextureStage.DEFERRED;
			default -> TextureStage.COMPOSITE_AND_FINAL;
		};
	}

	/** Iris's own composite transformation, with the pack's custom texture names for this stage as overrides. */
	static Map<PatchShaderType, String> transform(PackDirectives directives, ProgramSource source, TextureStage stage) {
		Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap = directives.getTextureMap();
		Set<String> overrides = textureMap.keySet().stream().filter(key -> key.third() == stage).map(Tri::first).collect(Collectors.toSet());
		return TransformPatcher.patchComposite(
			source.getName(), source.getVertexSource().orElse(null), source.getGeometrySource().orElse(null), source.getFragmentSource().orElse(null),
			stage, textureMap, overrides
		);
	}

	private static void toggle(Set<Integer> flipped, int buffer) {
		if (!flipped.remove(buffer)) {
			flipped.add(buffer);
		}
	}

	private Pass compile(PackDirectives directives, ProgramSource source, TextureStage stage, int[] drawBuffers, Set<Integer> flipped, boolean isFinal) {
		String name = source.getName();
		ProgramDirectives programDirectives = source.getDirectives();
		if (programDirectives.getBlendModeOverride().isPresent() || !programDirectives.getBufferBlendOverrides().isEmpty()) {
			warnOnce(name + ":blend", "{}: blend directives are not implemented on Vulkan yet; the pass renders without blending.", name);
		}
		if (!programDirectives.getMipmappedBuffers().isEmpty()) {
			warnOnce(name + ":mipmap", "{}: mipmapped buffers are not implemented on Vulkan yet; reads use mip level 0.", name);
		}
		if (programDirectives.getViewportScale().scale() != 1.0f) {
			warnOnce(name + ":scale", "{}: viewport scaling is not implemented on Vulkan yet; the pass renders at full size.", name);
		}

		Map<PatchShaderType, String> transformed = transform(directives, source, stage);

		List<GpuFormat> formats = new ArrayList<>();
		if (isFinal) {
			formats.add(GpuFormat.RGBA8_UNORM);
		} else {
			for (int buffer : drawBuffers) {
				formats.add(getOrCreateTarget(directives, buffer).format);
			}
		}

		IrisVulkanProgram program = IrisVulkanProgram.compile(packKey, name, transformed, uniforms.suppliedTypes(), formats);
		for (String sampler : program.shaderInterface.samplers()) {
			int index = colorTargetIndex(sampler);
			if (index >= 0) {
				getOrCreateTarget(directives, index);
			}
		}

		Iris.logger.info("[Iris/Vulkan] Compiled {} for Vulkan: {}", name, IrisVulkanGlslTranslator.describe(program.shaderInterface));
		return new Pass(name, program, drawBuffers, flipped);
	}

	private IrisVulkanTarget getOrCreateTarget(PackDirectives directives, int index) {
		IrisVulkanTarget existing = targets.get(index);
		if (existing != null) {
			return existing;
		}

		PackRenderTargetDirectives.RenderTargetSettings settings = directives.getRenderTargetDirectives().getRenderTargetSettings().get(index);
		if (settings == null) {
			throw new IllegalStateException("colortex" + index + " is not a supported render target");
		}

		IrisVulkanTarget target = new IrisVulkanTarget(index, IrisVulkanFormats.toRenderTargetFormat(settings.getInternalFormat()), width, height);
		targets.put(index, target);
		return target;
	}

	/** {@code colortexN} or a legacy alias ({@code gcolor}, {@code gaux1}, ...) to its index, else -1. */
	private static int colorTargetIndex(String sampler) {
		if (sampler.startsWith("colortex")) {
			try {
				return Integer.parseInt(sampler.substring("colortex".length()));
			} catch (NumberFormatException e) {
				return -1;
			}
		}

		return PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.indexOf(sampler);
	}

	/** Copies vanilla's world depth before the hand pass clears it (see GameRenderer#render3dHud). */
	void captureWorldDepth(CommandEncoder encoder, RenderTarget main) {
		GpuTexture depth = main.getDepthTexture();
		if (worldDepth == null || worldDepth.getWidth(0) != depth.getWidth(0) || worldDepth.getHeight(0) != depth.getHeight(0)) {
			releaseWorldDepth();
			worldDepth = RenderSystem.getDevice().createTexture("iris vulkan world depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
				depth.getFormat(), depth.getWidth(0), depth.getHeight(0), 1, 1);
			worldDepthView = RenderSystem.getDevice().createTextureView(worldDepth);
		}

		encoder.copyTextureToTexture(depth, worldDepth, 0, 0, 0, 0, 0, depth.getWidth(0), depth.getHeight(0));
	}

	void render(CommandEncoder encoder, RenderTarget main) {
		if (main.width != width || main.height != height) {
			width = main.width;
			height = main.height;
			targets.values().forEach(target -> target.resize(width, height));
		}

		updateNotifier.onNewFrame();
		clearColors.forEach((index, color) -> {
			IrisVulkanTarget target = targets.get(index);
			encoder.clearColorTexture(target.texture(false), color);
			encoder.clearColorTexture(target.texture(true), color);
		});

		for (Pass pass : earlyPasses) {
			draw(encoder, pass, main, null);
		}

		// The scene stands in for gbuffer output: it lands in the colortex0 texture the deferred stage reads.
		blit.blit(encoder, "scene to colortex0", main.getColorTextureView(), targets.get(0).view(flippedForScene.contains(0)));

		for (Pass pass : latePasses) {
			draw(encoder, pass, main, null);
		}

		if (finalPass != null) {
			draw(encoder, finalPass, main, main.getColorTextureView());
		} else {
			blit.blit(encoder, "colortex0 to screen", targets.get(0).view(flippedForFinal.contains(0)), main.getColorTextureView());
		}
	}

	private void draw(CommandEncoder encoder, Pass pass, RenderTarget main, GpuTextureView output) {
		IrisVulkanGlslTranslator.Result shaderInterface = pass.program.shaderInterface;
		GpuBufferSlice uniformSlice = null;
		if (!shaderInterface.uniformFields().isEmpty()) {
			ByteBuffer data = MemoryUtil.memCalloc(shaderInterface.uniformBlockSize());
			try {
				uniforms.write(shaderInterface.uniformFields(), data);
				uniformSlice = encoder.transientMemory().uploadGpu(
					data, RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM
				);
			} finally {
				MemoryUtil.memFree(data);
			}
		}

		RenderPassDescriptor.Builder descriptor = RenderPassDescriptor.builder(() -> "Iris Vulkan " + pass.name);
		if (output != null) {
			descriptor.withColorAttachment(output);
		} else {
			for (int buffer : pass.drawBuffers) {
				// Writes go to the texture the pass does not read: main when flipped, alt otherwise.
				descriptor.withColorAttachment(targets.get(buffer).view(!pass.flipped.contains(buffer)));
			}
		}

		try (RenderPass renderPass = encoder.createRenderPass(descriptor.build())) {
			renderPass.setPipeline(pass.program.pipeline);
			for (String sampler : shaderInterface.samplers()) {
				bindSampler(renderPass, pass, sampler, main);
			}
			if (uniformSlice != null) {
				renderPass.setUniform(IrisVulkanGlslTranslator.UNIFORM_BLOCK, uniformSlice);
			}
			renderPass.setVertexBuffer(0, quad.slice());
			renderPass.draw(6, 1, 0, 0);
		}
	}

	private void bindSampler(RenderPass renderPass, Pass pass, String sampler, RenderTarget main) {
		int index = colorTargetIndex(sampler);
		if (index >= 0) {
			IrisVulkanTarget target = targets.get(index);
			// Reads come from alt for flipped buffers, main otherwise (IrisSamplers#addRenderTargetSamplers).
			renderPass.setUniform(sampler, target.view(pass.flipped.contains(index)), target.sampler());
			return;
		}

		if (DEPTH_SAMPLERS.contains(sampler)) {
			if (!sampler.equals("depthtex0") && !sampler.equals("gdepthtex")) {
				warnOnce("depth:" + sampler, "{}: no translucent/hand-free depth on Vulkan yet; {} uses world depth.", pass.name, sampler);
			}
			GpuTextureView depth = worldDepthView != null ? worldDepthView : main.getDepthTextureView();
			renderPass.setUniform(sampler, depth, VulkanSamplers.clamp(false));
			return;
		}

		if (sampler.equals("noisetex")) {
			renderPass.setUniform(sampler, noiseView, VulkanSamplers.linearRepeat());
			return;
		}

		warnOnce("sampler:" + sampler, "{}: sampler {} is not available on Vulkan yet (shadow maps, custom textures); it reads transparent black.", pass.name, sampler);
		renderPass.setUniform(sampler, placeholderView, VulkanSamplers.clamp(false));
	}

	private void warnOnce(String key, String message, Object... args) {
		if (warned.add(key)) {
			Iris.logger.warn("[Iris/Vulkan] " + message, args);
		}
	}

	private static GpuBuffer createQuad() {
		// Iris's full-screen quad (FullScreenQuadRenderer) as two triangles: Position xyz, UV0 uv.
		float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 0}, {1, 1}, {0, 1}};
		ByteBuffer data = MemoryUtil.memAlloc(corners.length * QUAD_VERTEX_SIZE);
		try {
			for (float[] corner : corners) {
				data.putFloat(corner[0]).putFloat(corner[1]).putFloat(0.0f).putFloat(corner[0]).putFloat(corner[1]);
			}
			data.flip();
			return RenderSystem.getDevice().createBuffer(() -> "iris vulkan full-screen quad", GpuBuffer.USAGE_VERTEX, data);
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	private static GpuTexture createNoise(int size) {
		// Same bytes as Iris's OpenGL NoiseTexture (Random(0), RGB), expanded to RGBA8 with opaque alpha.
		byte[] rgb = new byte[size * size * 3];
		new Random(0L).nextBytes(rgb);
		ByteBuffer rgba = MemoryUtil.memAlloc(size * size * 4);
		try {
			for (int i = 0; i < size * size; i++) {
				rgba.put(rgb[i * 3]).put(rgb[i * 3 + 1]).put(rgb[i * 3 + 2]).put((byte) 0xFF);
			}
			rgba.flip();
			GpuTexture texture = RenderSystem.getDevice().createTexture("iris vulkan noisetex", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
				GpuFormat.RGBA8_UNORM, size, size, 1, 1);
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, rgba, 0, 0, 0, 0, size, size);
			return texture;
		} finally {
			MemoryUtil.memFree(rgba);
		}
	}

	private void releaseWorldDepth() {
		if (worldDepthView != null) {
			worldDepthView.close();
			worldDepth.close();
			worldDepthView = null;
			worldDepth = null;
		}
	}

	@Override
	public void close() {
		earlyPasses.forEach(pass -> pass.program.close());
		latePasses.forEach(pass -> pass.program.close());
		if (finalPass != null) {
			finalPass.program.close();
		}
		targets.values().forEach(IrisVulkanTarget::close);
		targets.clear();
		releaseWorldDepth();
		blit.close();
		quad.close();
		noiseView.close();
		noise.close();
		placeholderView.close();
		placeholder.close();
	}
}
