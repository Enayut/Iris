package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Full-screen texture copy between colour targets of possibly different formats (e.g. the RGBA8 main target into an
 * RGBA16F {@code colortex0}), which {@code CommandEncoder#copyTextureToTexture} cannot do. One pipeline per
 * destination format, same dialect as vanilla's post-processing shaders.
 */
final class IrisVulkanBlit implements AutoCloseable {
	private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("iris", "vulkan/blit.vsh");
	private static final Identifier FRAGMENT = Identifier.fromNamespaceAndPath("iris", "vulkan/blit.fsh");
	private static final String VERTEX_SOURCE = """
		#version 330
		#extension GL_ARB_separate_shader_objects : require

		layout(location = 0) out vec2 texCoord;

		void main() {
		    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
		    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
		    texCoord = uv;
		}
		""";
	private static final String FRAGMENT_SOURCE = """
		#version 330
		#extension GL_ARB_separate_shader_objects : require

		uniform sampler2D InSampler;

		layout(location = 0) in vec2 texCoord;
		layout(location = 0) out vec4 fragColor;

		void main() {
		    fragColor = texture(InSampler, texCoord);
		}
		""";

	private final Map<GpuFormat, CompiledRenderPipeline> pipelines = new EnumMap<>(GpuFormat.class);

	void blit(CommandEncoder encoder, String label, GpuTextureView source, GpuTextureView destination) {
		GpuFormat format = destination.texture().getFormat();
		if (IrisVulkanFormats.isInteger(format)) {
			throw new IllegalStateException("Cannot blit into integer render target format " + format);
		}

		CompiledRenderPipeline pipeline = pipelines.computeIfAbsent(format, IrisVulkanBlit::compile);
		try (RenderPass pass = encoder.createRenderPass(() -> "Iris Vulkan " + label, destination, Optional.empty())) {
			pass.setPipeline(pipeline);
			pass.setUniform("InSampler", source, VulkanSamplers.clamp(false));
			pass.draw(3, 1, 0, 0);
		}
	}

	private static CompiledRenderPipeline compile(GpuFormat format) {
		RenderPipeline pipeline = RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath("iris", "vulkan/blit/" + format.name().toLowerCase(Locale.ROOT)))
			.withVertexShader(VERTEX)
			.withFragmentShader(FRAGMENT)
			.withBindGroupLayout(BindGroupLayout.builder().withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER).build())
			.withColorTargetState(new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.build();

		CompiledRenderPipeline compiled;
		try (BlitSource source = new BlitSource()) {
			compiled = RenderSystem.getDevice().compilePipeline(pipeline, source, Runnable::run).join().finishCompile();
		}

		if (compiled == null) {
			throw new IllegalStateException("RenderPearl rejected the Iris blit pipeline for " + format + "; see latest.log");
		}

		return compiled;
	}

	@Override
	public void close() {
		pipelines.values().forEach(CompiledRenderPipeline::close);
		pipelines.clear();
	}

	private static final class BlitSource implements ShaderSource {
		@Override
		public String getShader(Identifier id, ShaderType type) {
			if (type == ShaderType.VERTEX && id.equals(VERTEX)) {
				return VERTEX_SOURCE;
			}

			return type == ShaderType.FRAGMENT && id.equals(FRAGMENT) ? FRAGMENT_SOURCE : null;
		}

		@Override
		public CachedIncludeSource getInclude(Identifier id) {
			return null;
		}

		@Override
		public void close() {
		}
	}
}
