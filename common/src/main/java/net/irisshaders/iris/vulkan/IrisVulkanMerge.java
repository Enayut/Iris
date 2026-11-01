package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Masked full-screen copies that combine the pack-lit terrain with what vanilla still draws on Vulkan (sky,
 * entities, translucent terrain, hand), using three depth snapshots of the main target (reverse-Z, cleared to 0):
 * {@code TerrainDepth} right after the terrain pass, {@code WorldDepth} before the hand pass clears depth, and
 * {@code FinalDepth} after it (non-zero only where the hand was drawn).
 *
 * <ul>
 *   <li>{@link Mode#TERRAIN_INTO_SCENE}: the pack's colortex0 into the main target wherever terrain was drawn, so
 *       vanilla's translucent terrain and entities later draw over the pack's terrain, not over the sky.</li>
 *   <li>{@link Mode#SCENE_OVER_TERRAIN}: the finished main target into colortex0 wherever the terrain is not the
 *       visible surface any more (sky, entities, water in front, hand), keeping the pack's full-precision colour
 *       elsewhere.</li>
 *   <li>{@link Mode#DEPTH_WITH_HAND}: a depth target holding {@code WorldDepth}, with the near plane (1 in reverse-Z)
 *       wherever the hand was drawn. That is the depth the pack's translucent terrain is tested against, so water
 *       never covers the hand, and what its depthtex0/depthtex1 read, as Iris's include the hand.</li>
 * </ul>
 */
final class IrisVulkanMerge implements AutoCloseable {
	enum Mode {
		TERRAIN_INTO_SCENE,
		SCENE_OVER_TERRAIN,
		DEPTH_WITH_HAND
	}

	private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("iris", "vulkan/merge.vsh");
	private static final Identifier FRAGMENT = Identifier.fromNamespaceAndPath("iris", "vulkan/merge.fsh");
	private static final Identifier DEPTH_FRAGMENT = Identifier.fromNamespaceAndPath("iris", "vulkan/merge_depth.fsh");
	private static final String VERTEX_SOURCE = """
		#version 330
		#extension GL_ARB_separate_shader_objects : require

		void main() {
		    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
		    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
		}
		""";
	// texelFetch at the fragment's own pixel: every input has the output's size, so no UV convention is involved.
	private static final String FRAGMENT_SOURCE = """
		#version 330
		#extension GL_ARB_separate_shader_objects : require

		uniform sampler2D InSampler;
		uniform sampler2D TerrainDepth;
		#ifdef SCENE_OVER_TERRAIN
		uniform sampler2D WorldDepth;
		uniform sampler2D FinalDepth;
		#endif

		layout(location = 0) out vec4 fragColor;

		void main() {
		    ivec2 pixel = ivec2(gl_FragCoord.xy);
		    float terrain = texelFetch(TerrainDepth, pixel, 0).r;
		#ifdef SCENE_OVER_TERRAIN
		    bool terrainVisible = terrain > 0.0 && texelFetch(WorldDepth, pixel, 0).r == terrain && texelFetch(FinalDepth, pixel, 0).r == 0.0;
		    if (terrainVisible) {
		        discard;
		    }
		#else
		    if (terrain <= 0.0) {
		        discard;
		    }
		#endif
		    fragColor = texelFetch(InSampler, pixel, 0);
		}
		""";
	private static final String DEPTH_FRAGMENT_SOURCE = """
		#version 330
		#extension GL_ARB_separate_shader_objects : require

		uniform sampler2D WorldDepth;
		uniform sampler2D FinalDepth;

		void main() {
		    ivec2 pixel = ivec2(gl_FragCoord.xy);
		    gl_FragDepth = texelFetch(FinalDepth, pixel, 0).r > 0.0 ? 1.0 : texelFetch(WorldDepth, pixel, 0).r;
		}
		""";

	private record Key(Mode mode, GpuFormat format) {
	}

	private final Map<Key, CompiledRenderPipeline> pipelines = new HashMap<>();

	void terrainIntoScene(CommandEncoder encoder, GpuTextureView terrainColor, GpuTextureView terrainDepth, GpuTextureView sceneColor) {
		try (RenderPass pass = open(encoder, Mode.TERRAIN_INTO_SCENE, sceneColor)) {
			pass.setUniform("InSampler", terrainColor, VulkanSamplers.clamp(false));
			pass.setUniform("TerrainDepth", terrainDepth, VulkanSamplers.clamp(false));
			pass.draw(3, 1, 0, 0);
		}
	}

	void sceneOverTerrain(
		CommandEncoder encoder, GpuTextureView sceneColor, GpuTextureView terrainDepth, GpuTextureView worldDepth, GpuTextureView finalDepth, GpuTextureView terrainColor
	) {
		try (RenderPass pass = open(encoder, Mode.SCENE_OVER_TERRAIN, terrainColor)) {
			pass.setUniform("InSampler", sceneColor, VulkanSamplers.clamp(false));
			pass.setUniform("TerrainDepth", terrainDepth, VulkanSamplers.clamp(false));
			pass.setUniform("WorldDepth", worldDepth, VulkanSamplers.clamp(false));
			pass.setUniform("FinalDepth", finalDepth, VulkanSamplers.clamp(false));
			pass.draw(3, 1, 0, 0);
		}
	}

	void depthWithHand(CommandEncoder encoder, GpuTextureView worldDepth, GpuTextureView finalDepth, GpuTextureView destinationDepth) {
		try (RenderPass pass = open(encoder, Mode.DEPTH_WITH_HAND, destinationDepth)) {
			pass.setUniform("WorldDepth", worldDepth, VulkanSamplers.clamp(false));
			pass.setUniform("FinalDepth", finalDepth, VulkanSamplers.clamp(false));
			pass.draw(3, 1, 0, 0);
		}
	}

	/** {@code destination} is the colour attachment, or the depth attachment for {@link Mode#DEPTH_WITH_HAND}. */
	private RenderPass open(CommandEncoder encoder, Mode mode, GpuTextureView destination) {
		GpuFormat format = destination.texture().getFormat();
		if (mode != Mode.DEPTH_WITH_HAND && IrisVulkanFormats.isInteger(format)) {
			throw new IllegalStateException("Cannot merge into integer render target format " + format);
		}

		CompiledRenderPipeline pipeline = pipelines.computeIfAbsent(new Key(mode, format), IrisVulkanMerge::compile);
		RenderPassDescriptor.Builder descriptor = RenderPassDescriptor.builder(() -> "Iris Vulkan merge " + mode.name().toLowerCase(Locale.ROOT));
		if (mode == Mode.DEPTH_WITH_HAND) {
			descriptor.withDepthAttachment(destination);
		} else {
			descriptor.withColorAttachment(destination);
		}
		RenderPass pass = encoder.createRenderPass(descriptor.build());
		try {
			pass.setPipeline(pipeline);
		} catch (RuntimeException e) {
			pass.close();
			throw e;
		}
		return pass;
	}

	private static CompiledRenderPipeline compile(Key key) {
		boolean depth = key.mode == Mode.DEPTH_WITH_HAND;
		BindGroupLayout.Builder layout = BindGroupLayout.builder();
		if (!depth) {
			layout.withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("TerrainDepth", UniformType.COMBINED_IMAGE_SAMPLER);
		}
		if (key.mode != Mode.TERRAIN_INTO_SCENE) {
			layout.withUniform("WorldDepth", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("FinalDepth", UniformType.COMBINED_IMAGE_SAMPLER);
		}

		Identifier fragment = depth ? DEPTH_FRAGMENT : FRAGMENT;
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath("iris", "vulkan/merge/" + key.mode.name().toLowerCase(Locale.ROOT) + "/" + key.format.name().toLowerCase(Locale.ROOT)))
			.withVertexShader(VERTEX)
			.withFragmentShader(fragment)
			.withBindGroupLayout(layout.build())
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
		if (depth) {
			builder.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true));
		} else {
			builder.withColorTargetState(new ColorTargetState(Optional.empty(), key.format, ColorTargetState.WRITE_ALL));
		}
		if (key.mode == Mode.SCENE_OVER_TERRAIN) {
			builder.withShaderDefine("SCENE_OVER_TERRAIN");
		}

		CompiledRenderPipeline compiled;
		try (IrisVulkanProgram.TranslatedSource source = new IrisVulkanProgram.TranslatedSource(VERTEX, VERTEX_SOURCE, fragment, depth ? DEPTH_FRAGMENT_SOURCE : FRAGMENT_SOURCE)) {
			compiled = RenderSystem.getDevice().compilePipeline(builder.build(), source, Runnable::run).join().finishCompile();
		}

		if (compiled == null) {
			throw new IllegalStateException("RenderPearl rejected the Iris merge pipeline " + key + "; see latest.log");
		}

		return compiled;
	}

	@Override
	public void close() {
		pipelines.values().forEach(CompiledRenderPipeline::close);
		pipelines.clear();
	}
}
