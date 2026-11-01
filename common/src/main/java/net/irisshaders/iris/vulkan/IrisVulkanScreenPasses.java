package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.sodium.mixin.SodiumChunkSectionAccessor;
import net.irisshaders.iris.compat.sodium.mixin.SodiumWorldRendererAccessor;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.shaderpack.loading.ProgramArrayId;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

/**
 * The Vulkan renderer's per-pack state: a shader pack's begin/prepare/deferred/composite/final passes, and (milestone
 * 2) its gbuffers_terrain and gbuffers_water programs for Sodium's terrain ({@link IrisVulkanTerrain}).
 *
 * <p>A frame: {@link #beginFrame} (clears, begin and prepare) runs just before vanilla's main pass, followed by
 * {@link #renderTerrain} for the opaque terrain when the pack has terrain programs. Vanilla then draws everything else
 * except the translucent terrain into its main target, and {@link #render} merges that scene into {@code colortex0}
 * wherever the pack's terrain is not the visible surface ({@link IrisVulkanMerge}), runs deferred, draws the
 * translucent terrain with the pack's program (as Iris does after deferred), then composite and final. Without terrain
 * programs {@code colortex0} receives the whole vanilla scene, like a pack without gbuffer programs on OpenGL.</p>
 *
 * <p>Depth textures follow Iris on terrain frames: depthtex0 has everything, depthtex1 no translucent terrain,
 * depthtex2 neither translucent terrain nor the hand. The hand's pixels read the near plane in depthtex0/1. Other
 * frames read vanilla's world depth for all three.</p>
 *
 * <p>Render target handling follows Iris's OpenGL {@code CompositeRenderer}: one buffer flipper shared across the
 * stages, a pass reads {@code alt} for flipped buffers and {@code main} otherwise and writes the opposite, written
 * buffers flip afterwards unless {@code flip.<pass>.<buffer>=false}, and {@code <stage>_pre} flips apply first.</p>
 */
final class IrisVulkanScreenPasses implements AutoCloseable {
	private static final ProgramArrayId[] EARLY_STAGES = {ProgramArrayId.Begin, ProgramArrayId.Prepare};
	private static final Set<String> DEPTH_SAMPLERS = Set.of("depthtex0", "gdepthtex", "depthtex1", "depthtex2");
	private static final int QUAD_VERTEX_SIZE = 20;

	private record Pass(String name, IrisVulkanProgram program, TextureStage stage, int[] drawBuffers, Set<Integer> flipped) {
	}

	/**
	 * What depthtex0, depthtex1 and depthtex2 read in a pass; {@code separated} is false when all three are the same
	 * world depth.
	 */
	private record DepthViews(GpuTextureView depth0, GpuTextureView depth1, GpuTextureView depth2, boolean separated) {
		static DepthViews all(GpuTextureView depth) {
			return new DepthViews(depth, depth, depth, false);
		}
	}

	private final String packKey;
	private final FrameUpdateNotifier updateNotifier;
	private final IrisVulkanUniforms uniforms;
	private final Map<Integer, IrisVulkanTarget> targets = new HashMap<>();
	private final Map<Integer, Vector4f> clearColors = new HashMap<>();
	private final List<Pass> earlyPasses = new ArrayList<>();
	private final List<Pass> deferredPasses = new ArrayList<>();
	private final List<Pass> compositePasses = new ArrayList<>();
	private final Set<Integer> flippedForScene;
	/** Flip state after deferred, which the translucent terrain writes and reads (Iris's flippedAfterTranslucent). */
	private final Set<Integer> flippedForTranslucent;
	private final Pass finalPass;
	private final Set<Integer> flippedForFinal;
	private final Set<String> warned = new HashSet<>();
	private final IrisVulkanBlit blit = new IrisVulkanBlit();
	// Assigned inside the constructor's try block so a failed build can release whatever was created.
	private GpuBuffer quad;
	private GpuTexture noise;
	private GpuTextureView noiseView;
	private GpuTexture placeholder;
	private GpuTextureView placeholderView;
	private GpuTexture shadowPlaceholder;
	private GpuTextureView shadowPlaceholderView;
	private GpuTexture normalsPlaceholder;
	private GpuTextureView normalsPlaceholderView;
	private IrisVulkanCustomTextures customTextures;
	private static final int COPY_USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;
	private final TextureCopy worldDepth = new TextureCopy("iris vulkan world depth", COPY_USAGE);
	private final TextureCopy terrainDepth = new TextureCopy("iris vulkan terrain depth", COPY_USAGE);
	private final TextureCopy finalDepth = new TextureCopy("iris vulkan final depth", COPY_USAGE);
	/** depthtex0 on terrain frames: world depth with the hand, plus the translucent terrain once drawn. */
	private final TextureCopy sceneDepth = new TextureCopy("iris vulkan scene depth",
		GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC);
	/** depthtex1 on terrain frames: {@link #sceneDepth} before the translucent terrain. */
	private final TextureCopy noTranslucentsDepth = new TextureCopy("iris vulkan no translucents depth", COPY_USAGE);
	/** Copies of colour targets a gbuffer program both reads and writes, taken just before it draws. */
	private final Map<Integer, TextureCopy> feedbackCopies = new HashMap<>();
	/** Opaque terrain programs, solid then cutout. */
	private final Map<IrisVulkanTerrain.Layer, IrisVulkanTerrain.Program> terrain = new EnumMap<>(IrisVulkanTerrain.Layer.class);
	private IrisVulkanTerrain.Program translucent;
	private final IrisVulkanMerge merge = new IrisVulkanMerge();
	private int width;
	private int height;
	/** {@link SystemTimeUniforms#COUNTER} of the frame {@link #beginFrame} last ran for, or -1. */
	private int begunFrame = -1;
	private boolean terrainDrawn;
	/** This frame's Sodium sections while the translucent terrain waits for {@link #render}, else null. */
	private SodiumChunkSectionAccessor pendingTranslucent;
	private boolean translucentDrawn;

	/**
	 * Passes for a pack, fed by Iris's standard uniform suppliers.
	 *
	 * @param terrainFormat Sodium's chunk vertex format the terrain programs are built for, or null for no terrain
	 */
	static IrisVulkanScreenPasses create(String packKey, ProgramSet programSet, int width, int height, VertexFormat terrainFormat) {
		FrameUpdateNotifier updateNotifier = new FrameUpdateNotifier();
		IrisVulkanUniforms uniforms = IrisVulkanUniforms.forScreenPasses(programSet.getPack().getIdMap(), programSet.getPackDirectives(), updateNotifier);
		IrisVulkanTerrain.addUniforms(uniforms);
		return new IrisVulkanScreenPasses(packKey, programSet, uniforms, updateNotifier, width, height, terrainFormat);
	}

	/** Screen passes only. {@code uniforms} must have been registered against {@code updateNotifier}. */
	IrisVulkanScreenPasses(String packKey, ProgramSet programSet, IrisVulkanUniforms uniforms, FrameUpdateNotifier updateNotifier, int width, int height) {
		this(packKey, programSet, uniforms, updateNotifier, width, height, null);
	}

	IrisVulkanScreenPasses(
		String packKey, ProgramSet programSet, IrisVulkanUniforms uniforms, FrameUpdateNotifier updateNotifier, int width, int height, VertexFormat terrainFormat
	) {
		this.packKey = packKey;
		this.width = width;
		this.height = height;
		PackDirectives directives = programSet.getPackDirectives();
		this.updateNotifier = updateNotifier;
		this.uniforms = uniforms;
		try {
			this.quad = createQuad();
			this.noise = createNoise(directives.getNoiseTextureResolution());
			this.noiseView = RenderSystem.getDevice().createTextureView(noise);
			this.placeholder = createSolid("iris vulkan placeholder", 0, 0, 0, 0);
			this.placeholderView = RenderSystem.getDevice().createTextureView(placeholder);
			// No shadow pass yet: shadow maps read "nothing in shadow" (far depth, white shadow colour), which is
			// what the pack sees with shadows disabled, instead of everything being shadowed.
			this.shadowPlaceholder = createSolid("iris vulkan shadow placeholder", 255, 255, 255, 255);
			this.shadowPlaceholderView = RenderSystem.getDevice().createTextureView(shadowPlaceholder);
			// No PBR textures on Vulkan yet: a flat tangent-space normal, as Iris's default normal texture.
			this.normalsPlaceholder = createSolid("iris vulkan normals placeholder", 128, 128, 255, 255);
			this.normalsPlaceholderView = RenderSystem.getDevice().createTextureView(normalsPlaceholder);
			this.customTextures = new IrisVulkanCustomTextures(programSet.getPack());
			getOrCreateTarget(directives, 0);
			if (terrainFormat != null) {
				compileTerrain(programSet, directives, terrainFormat);
			}
			Set<Integer> flipped = new LinkedHashSet<>();
			for (ProgramArrayId stage : EARLY_STAGES) {
				planStage(programSet, directives, stage, flipped, earlyPasses);
			}
			this.flippedForScene = Set.copyOf(flipped);
			planStage(programSet, directives, ProgramArrayId.Deferred, flipped, deferredPasses);
			this.flippedForTranslucent = Set.copyOf(flipped);
			planStage(programSet, directives, ProgramArrayId.Composite, flipped, compositePasses);
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

		Iris.logger.info("[Iris/Vulkan] Built screen passes for {}: {} early, {} deferred, {} composite, final={}, targets={}, {} supplied uniforms.",
			packKey, earlyPasses.size(), deferredPasses.size(), compositePasses.size(), finalPass != null, targets.keySet(), uniforms.suppliedTypes().size());
	}

	/**
	 * Both opaque layers or neither: the main pass skips Sodium's whole opaque group once Iris has drawn it, so a
	 * layer without a program would disappear. The translucent layer is optional (vanilla draws it otherwise) and
	 * needs the opaque ones, whose depth and merge it relies on.
	 */
	private void compileTerrain(ProgramSet programSet, PackDirectives directives, VertexFormat vertexFormat) {
		ProgramFallbackResolver resolver = new ProgramFallbackResolver(programSet);
		Map<IrisVulkanTerrain.Layer, ProgramSource> sources = new EnumMap<>(IrisVulkanTerrain.Layer.class);
		for (IrisVulkanTerrain.Layer layer : IrisVulkanTerrain.Layer.values()) {
			ProgramSource source = resolver.resolveNullable(layer.programId);
			if (source == null || !source.isValid()) {
				if (!layer.isTranslucent()) {
					Iris.logger.info("[Iris/Vulkan] Pack has no program for {} terrain; terrain stays vanilla on Vulkan.", layer.name().toLowerCase(java.util.Locale.ROOT));
					return;
				}
				Iris.logger.info("[Iris/Vulkan] Pack has no program for translucent terrain; water stays vanilla on Vulkan.");
				continue;
			}
			sources.put(layer, source);
		}

		sources.forEach((layer, source) -> {
			ProgramDirectives programDirectives = source.getDirectives();
			int[] drawBuffers = programDirectives.getDrawBuffers();
			List<GpuFormat> formats = new ArrayList<>();
			for (int buffer : drawBuffers) {
				formats.add(getOrCreateTarget(directives, buffer).format);
			}

			IrisVulkanTerrain.Program program = IrisVulkanTerrain.compile(packKey, directives, source, layer, uniforms.suppliedTypes(), vertexFormat, drawBuffers, formats);
			if (layer.isTranslucent()) {
				translucent = program;
			} else {
				terrain.put(layer, program);
			}
			for (String sampler : program.shaderInterface().samplers()) {
				int index = colorTargetIndex(sampler);
				if (index >= 0) {
					getOrCreateTarget(directives, index);
				}
			}

			Iris.logger.info("[Iris/Vulkan] Registered terrain program {} for {} terrain: writes {} blending {}; {}", source.getName(),
				layer.name().toLowerCase(java.util.Locale.ROOT), java.util.Arrays.toString(drawBuffers),
				IrisVulkanTerrain.blending(programDirectives, layer, drawBuffers, formats).stream().map(blend -> blend.map(Object::toString).orElse("off")).toList(),
				IrisVulkanGlslTranslator.describe(program.shaderInterface()));
		});
	}

	boolean hasTerrain() {
		return !terrain.isEmpty();
	}

	boolean hasTranslucentTerrain() {
		return translucent != null;
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

		Iris.logger.info("[Iris/Vulkan] Registered pass {} ({}): writes {} reading flipped {}; {}", name, isFinal ? "final, to screen" : stage,
			isFinal ? "main target" : java.util.Arrays.toString(drawBuffers), flipped, IrisVulkanGlslTranslator.describe(program.shaderInterface));
		return new Pass(name, program, stage, drawBuffers, flipped);
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
		worldDepth.copy(encoder, main.getDepthTexture());
	}

	/**
	 * Start of a world frame, before anything is drawn into the pack's targets: resize, per-frame uniforms, clears and
	 * the begin/prepare passes. Runs once per frame; {@link #render} calls it too in case no main pass ran.
	 */
	void beginFrame(CommandEncoder encoder, RenderTarget main) {
		int frame = SystemTimeUniforms.COUNTER.getAsInt();
		if (begunFrame == frame) {
			return;
		}
		begunFrame = frame;
		terrainDrawn = false;
		pendingTranslucent = null;
		translucentDrawn = false;

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

		DepthViews depths = screenDepths(main);
		for (Pass pass : earlyPasses) {
			draw(encoder, pass, null, depths);
		}
	}

	/** Vanilla's world depth for all three depth textures: last frame's copy, or the main target before the first. */
	private DepthViews screenDepths(RenderTarget main) {
		return DepthViews.all(worldDepth.view() != null ? worldDepth.view() : main.getDepthTextureView());
	}

	/**
	 * Draws Sodium's solid and cutout terrain with the pack's programs into the pack's targets and vanilla's depth,
	 * then copies the result into vanilla's colour target so that what vanilla draws next lands on top of it. When the
	 * pack also has a translucent terrain program and vanilla uses classic transparency, the translucent terrain is
	 * left for {@link #render} ({@link #translucentTerrainPending}).
	 *
	 * @return whether the terrain was drawn, i.e. the main pass must skip it
	 */
	boolean renderTerrain(CommandEncoder encoder, RenderTarget main, SodiumChunkSectionAccessor sections) {
		if (terrain.isEmpty() || terrainDrawn) {
			return terrainDrawn;
		}

		// Gbuffer passes render into main's depth, so they read last frame's world depth, or the placeholder (far plane).
		DepthViews depths = DepthViews.all(worldDepth.view() != null ? worldDepth.view() : placeholderView);
		for (IrisVulkanTerrain.Program program : terrain.values()) {
			drawTerrainLayer(encoder, program, sections, flippedForScene, main.getDepthTextureView(), depths);
		}

		terrainDepth.copy(encoder, main.getDepthTexture());
		merge.terrainIntoScene(encoder, targets.get(0).view(flippedForScene.contains(0)), terrainDepth.view(), main.getColorTextureView());
		terrainDrawn = true;
		// Improved transparency (OIT) draws the translucent terrain in its own passes, which stay vanilla's.
		if (translucent != null && !Minecraft.getInstance().gameRenderer.useImprovedTransparency()) {
			pendingTranslucent = sections;
		}
		return true;
	}

	/**
	 * One terrain layer through Sodium's draw loop with the pack's program.
	 *
	 * @param flipped the flip state the program writes and reads, as the stage it runs in
	 */
	private void drawTerrainLayer(
		CommandEncoder encoder, IrisVulkanTerrain.Program program, SodiumChunkSectionAccessor sections, Set<Integer> flipped, GpuTextureView depthAttachment,
		DepthViews depths
	) {
		SodiumWorldRenderer renderer = sections.iris$getRenderer();
		FogParameters fog = ((SodiumWorldRendererAccessor) renderer).iris$getLastFogParameters();
		GpuTextureView atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
		// Packs expect unfiltered block textures, as Iris forces on OpenGL (MixinDefaultChunkRenderer).
		GpuSampler atlasSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);

		IrisVulkanTerrain.prepareUniforms(program, sections.iris$getMatrices().modelView());
		GpuBufferSlice uniformSlice = uploadUniforms(encoder, program.shaderInterface());
		Map<Integer, GpuTextureView> feedback = copyFeedback(encoder, program, flipped);

		RenderPassDescriptor.Builder descriptor = RenderPassDescriptor.builder(() -> "Iris Vulkan " + program.name() + " (" + program.layer() + " terrain)");
		for (int buffer : program.drawBuffers()) {
			// Gbuffers write the textures the following stage reads, like the scene copy they replace.
			descriptor.withColorAttachment(targets.get(buffer).view(flipped.contains(buffer)));
		}
		descriptor.withDepthAttachment(depthAttachment);

		try (RenderPass renderPass = encoder.createRenderPass(descriptor.build())) {
			IrisVulkanTerrain.begin(program, pass -> bindTerrain(pass, program, uniformSlice, atlas, atlasSampler, flipped, feedback, depths));
			renderer.renderLayer(
				sections.iris$getMatrices(), program.layer().sodiumPass(), sections.iris$getX(), sections.iris$getY(), sections.iris$getZ(),
				fog, atlasSampler, renderPass, null
			);
		} finally {
			IrisVulkanTerrain.end();
		}
	}

	/**
	 * A render pass cannot sample its own attachment, so the colour targets the program both reads and writes are
	 * copied first; it then reads them as they were before it drew, as on OpenGL apart from overlapping geometry.
	 */
	private Map<Integer, GpuTextureView> copyFeedback(CommandEncoder encoder, IrisVulkanTerrain.Program program, Set<Integer> flipped) {
		Map<Integer, GpuTextureView> copies = new HashMap<>();
		for (String sampler : program.shaderInterface().samplers()) {
			int index = colorTargetIndex(sampler);
			if (index >= 0 && !copies.containsKey(index) && java.util.Arrays.stream(program.drawBuffers()).anyMatch(buffer -> buffer == index)) {
				TextureCopy copy = feedbackCopies.computeIfAbsent(index, i -> new TextureCopy("iris vulkan colortex" + i + " feedback copy", COPY_USAGE));
				copy.copy(encoder, targets.get(index).texture(flipped.contains(index)));
				copies.put(index, copy.view());
			}
		}
		return copies;
	}

	boolean terrainDrawnThisFrame() {
		return terrainDrawn && begunFrame == SystemTimeUniforms.COUNTER.getAsInt();
	}

	/** Whether this frame's translucent terrain is left for {@link #render}, i.e. the main pass must skip it. */
	boolean translucentTerrainPending() {
		return pendingTranslucent != null && terrainDrawnThisFrame();
	}

	private void bindTerrain(
		RenderPass renderPass, IrisVulkanTerrain.Program program, GpuBufferSlice uniformSlice, GpuTextureView atlas, GpuSampler atlasSampler, Set<Integer> flipped,
		Map<Integer, GpuTextureView> feedback, DepthViews depths
	) {
		for (String sampler : program.shaderInterface().samplers()) {
			switch (sampler) {
				case "tex", "texture", "gtexture" -> renderPass.setUniform(sampler, atlas, atlasSampler);
				case "lightmap" -> renderPass.setUniform(sampler, Minecraft.getInstance().gameRenderer.lightmap(), VulkanSamplers.clamp(true));
				case "normals" -> renderPass.setUniform(sampler, normalsPlaceholderView, VulkanSamplers.clamp(false));
				case "specular" -> renderPass.setUniform(sampler, placeholderView, VulkanSamplers.clamp(false));
				default -> bindSampler(renderPass, program.name(), TextureStage.GBUFFERS_AND_SHADOW, flipped, feedback, sampler, depths);
			}
		}

		if (uniformSlice != null) {
			renderPass.setUniform(IrisVulkanGlslTranslator.UNIFORM_BLOCK, uniformSlice);
		}
	}

	void render(CommandEncoder encoder, RenderTarget main) {
		beginFrame(encoder, main);

		DepthViews depths;
		if (terrainDrawnThisFrame()) {
			finalDepth.copy(encoder, main.getDepthTexture());
			GpuTextureView world = worldDepth.view() != null ? worldDepth.view() : terrainDepth.view();
			merge.sceneOverTerrain(encoder, main.getColorTextureView(), terrainDepth.view(), world, finalDepth.view(), targets.get(0).view(flippedForScene.contains(0)));
			sceneDepth.allocate(main.getDepthTexture());
			merge.depthWithHand(encoder, world, finalDepth.view(), sceneDepth.view());
			noTranslucentsDepth.copy(encoder, sceneDepth.texture());
			depths = new DepthViews(sceneDepth.view(), noTranslucentsDepth.view(), world, true);
		} else {
			// The scene stands in for gbuffer output: it lands in the colortex0 texture the deferred stage reads.
			blit.blit(encoder, "scene to colortex0", main.getColorTextureView(), targets.get(0).view(flippedForScene.contains(0)));
			depths = screenDepths(main);
		}

		for (Pass pass : deferredPasses) {
			draw(encoder, pass, null, depths);
		}

		if (translucentTerrainPending()) {
			// Tested against and written into depthtex0's texture, so the program reads depthtex1 for both.
			drawTerrainLayer(encoder, translucent, pendingTranslucent, flippedForTranslucent, sceneDepth.view(),
				new DepthViews(depths.depth1(), depths.depth1(), depths.depth2(), true));
			pendingTranslucent = null;
			translucentDrawn = true;
		}

		for (Pass pass : compositePasses) {
			draw(encoder, pass, null, depths);
		}

		if (finalPass != null) {
			draw(encoder, finalPass, main.getColorTextureView(), depths);
		} else {
			blit.blit(encoder, "colortex0 to screen", targets.get(0).view(flippedForFinal.contains(0)), main.getColorTextureView());
		}
	}

	/** Names of the registered passes, in execution order. */
	List<String> passNames() {
		List<String> names = new ArrayList<>();
		earlyPasses.forEach(pass -> names.add(pass.name));
		deferredPasses.forEach(pass -> names.add(pass.name));
		compositePasses.forEach(pass -> names.add(pass.name));
		names.add(finalPass != null ? finalPass.name : "(no final: colortex0 copied to screen)");
		return names;
	}

	/** What one frame executes, including the steps that are not pack programs. */
	String executionOrder() {
		List<String> steps = new ArrayList<>();
		if (!clearColors.isEmpty()) {
			steps.add("clear colortex" + clearColors.keySet());
		}
		earlyPasses.forEach(pass -> steps.add(pass.name));
		if (terrainDrawnThisFrame()) {
			terrain.values().forEach(program -> steps.add(program.name() + " (" + program.layer().name().toLowerCase(java.util.Locale.ROOT) + " terrain)"));
			steps.add("scene -> colortex0 where terrain is hidden");
			steps.add("depthtex0/1/2 split (hand at near plane)");
		} else {
			steps.add("scene -> colortex0");
		}
		deferredPasses.forEach(pass -> steps.add(pass.name));
		if (translucentDrawn && begunFrame == SystemTimeUniforms.COUNTER.getAsInt()) {
			steps.add(translucent.name() + " (translucent terrain)");
		}
		compositePasses.forEach(pass -> steps.add(pass.name));
		steps.add(finalPass != null ? finalPass.name + " -> screen" : "colortex0 -> screen");
		return String.join(", ", steps);
	}

	private void draw(CommandEncoder encoder, Pass pass, GpuTextureView output, DepthViews depths) {
		IrisVulkanGlslTranslator.Result shaderInterface = pass.program.shaderInterface;
		GpuBufferSlice uniformSlice = uploadUniforms(encoder, shaderInterface);

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
				bindSampler(renderPass, pass.name, pass.stage, pass.flipped, null, sampler, depths);
			}
			if (uniformSlice != null) {
				renderPass.setUniform(IrisVulkanGlslTranslator.UNIFORM_BLOCK, uniformSlice);
			}
			renderPass.setVertexBuffer(0, quad.slice());
			renderPass.draw(6, 1, 0, 0);
		}
	}

	/** The pass's uniform block with current values, or null when its programs read no Iris uniforms. */
	private GpuBufferSlice uploadUniforms(CommandEncoder encoder, IrisVulkanGlslTranslator.Result shaderInterface) {
		if (shaderInterface.uniformFields().isEmpty()) {
			return null;
		}

		ByteBuffer data = MemoryUtil.memCalloc(shaderInterface.uniformBlockSize());
		try {
			uniforms.write(shaderInterface.uniformFields(), data);
			return encoder.transientMemory().uploadGpu(data, RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	/**
	 * @param flipped  the pass's flip snapshot: reads come from alt for flipped buffers, main otherwise
	 * @param feedback for gbuffer passes, copies of the colour targets they render to ({@link #copyFeedback}); null
	 *                 for screen passes
	 */
	private void bindSampler(
		RenderPass renderPass, String passName, TextureStage stage, Set<Integer> flipped, Map<Integer, GpuTextureView> feedback, String sampler, DepthViews depths
	) {
		// texture.<stage>.<sampler> takes priority over render targets, as Iris's customTextureSamplerInterceptor.
		IrisVulkanCustomTextures.Texture custom = customTextures.forStage(stage, sampler);
		if (custom != null) {
			renderPass.setUniform(sampler, custom.view(), custom.sampler());
			return;
		}

		int index = colorTargetIndex(sampler);
		if (index >= 0) {
			IrisVulkanTarget target = targets.get(index);
			GpuTextureView copy = feedback != null ? feedback.get(index) : null;
			// Reads come from alt for flipped buffers, main otherwise (IrisSamplers#addRenderTargetSamplers).
			renderPass.setUniform(sampler, copy != null ? copy : target.view(flipped.contains(index)), target.sampler());
			return;
		}

		if (DEPTH_SAMPLERS.contains(sampler)) {
			if (!depths.separated() && !sampler.equals("depthtex0") && !sampler.equals("gdepthtex")) {
				warnOnce("depth:" + passName + ":" + sampler, "{}: no translucent/hand-free depth in this pass on Vulkan; {} uses world depth.", passName, sampler);
			}
			GpuTextureView depth = switch (sampler) {
				case "depthtex1" -> depths.depth1();
				case "depthtex2" -> depths.depth2();
				default -> depths.depth0();
			};
			renderPass.setUniform(sampler, depth, VulkanSamplers.clamp(false));
			return;
		}

		if (sampler.equals("noisetex")) {
			IrisVulkanCustomTextures.Texture noise = customTextures.noise();
			if (noise != null) {
				renderPass.setUniform(sampler, noise.view(), noise.sampler());
			} else {
				renderPass.setUniform(sampler, noiseView, VulkanSamplers.linearRepeat());
			}
			return;
		}

		if (sampler.startsWith("shadowtex") || sampler.startsWith("shadowcolor") || sampler.equals("shadow") || sampler.equals("watershadow")) {
			warnOnce("shadow:" + sampler, "{}: no shadow pass on Vulkan yet; {} reads as unshadowed.", passName, sampler);
			renderPass.setUniform(sampler, shadowPlaceholderView, VulkanSamplers.clamp(false));
			return;
		}

		custom = customTextures.forEveryStage(sampler);
		if (custom != null) {
			renderPass.setUniform(sampler, custom.view(), custom.sampler());
			return;
		}

		warnOnce("sampler:" + sampler, "{}: sampler {} is not available on Vulkan yet (custom textures); it reads transparent black.", passName, sampler);
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

	/**
	 * 1x1 RGBA8 texture of one colour, bound for samplers Vulkan cannot provide yet. Only ever sampled, so its texel is
	 * uploaded rather than cleared: RenderPearl only clears textures that are render attachments (verifyColorTexture).
	 */
	private static GpuTexture createSolid(String label, int r, int g, int b, int a) {
		GpuTexture texture = RenderSystem.getDevice().createTexture(label, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
			GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
		ByteBuffer texel = MemoryUtil.memAlloc(4).put((byte) r).put((byte) g).put((byte) b).put((byte) a).flip();
		try {
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, texel, 0, 0, 0, 0, 1, 1);
			return texture;
		} catch (RuntimeException e) {
			texture.close();
			throw e;
		} finally {
			MemoryUtil.memFree(texel);
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
			try {
				RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, rgba, 0, 0, 0, 0, size, size);
			} catch (RuntimeException e) {
				texture.close();
				throw e;
			}
			return texture;
		} finally {
			MemoryUtil.memFree(rgba);
		}
	}

	/** A texture shaped like another (size and format), reallocated when that changes; usually a copy of it. */
	private static final class TextureCopy implements AutoCloseable {
		private final String label;
		private final int usage;
		private GpuTexture texture;
		private GpuTextureView view;

		TextureCopy(String label, int usage) {
			this.label = label;
			this.usage = usage;
		}

		void allocate(GpuTexture like) {
			if (texture == null || texture.getWidth(0) != like.getWidth(0) || texture.getHeight(0) != like.getHeight(0) || texture.getFormat() != like.getFormat()) {
				close();
				texture = RenderSystem.getDevice().createTexture(label, usage, like.getFormat(), like.getWidth(0), like.getHeight(0), 1, 1);
				view = RenderSystem.getDevice().createTextureView(texture);
			}
		}

		void copy(CommandEncoder encoder, GpuTexture source) {
			allocate(source);
			encoder.copyTextureToTexture(source, texture, 0, 0, 0, 0, 0, source.getWidth(0), source.getHeight(0));
		}

		GpuTexture texture() {
			return texture;
		}

		GpuTextureView view() {
			return view;
		}

		@Override
		public void close() {
			if (view != null) {
				view.close();
				texture.close();
				view = null;
				texture = null;
			}
		}
	}

	@Override
	public void close() {
		earlyPasses.forEach(pass -> pass.program.close());
		deferredPasses.forEach(pass -> pass.program.close());
		compositePasses.forEach(pass -> pass.program.close());
		if (finalPass != null) {
			finalPass.program.close();
		}
		terrain.values().forEach(IrisVulkanTerrain.Program::close);
		terrain.clear();
		if (translucent != null) {
			translucent.close();
			translucent = null;
		}
		targets.values().forEach(IrisVulkanTarget::close);
		targets.clear();
		worldDepth.close();
		terrainDepth.close();
		finalDepth.close();
		sceneDepth.close();
		noTranslucentsDepth.close();
		feedbackCopies.values().forEach(TextureCopy::close);
		feedbackCopies.clear();
		blit.close();
		merge.close();
		closeIfCreated(quad);
		closeIfCreated(noiseView);
		closeIfCreated(noise);
		closeIfCreated(placeholderView);
		closeIfCreated(placeholder);
		closeIfCreated(shadowPlaceholderView);
		closeIfCreated(shadowPlaceholder);
		closeIfCreated(normalsPlaceholderView);
		closeIfCreated(normalsPlaceholder);
		closeIfCreated(customTextures);
	}

	private static void closeIfCreated(AutoCloseable resource) {
		if (resource != null) {
			try {
				resource.close();
			} catch (Exception e) {
				throw new IllegalStateException("Failed to release " + resource, e);
			}
		}
	}
}
