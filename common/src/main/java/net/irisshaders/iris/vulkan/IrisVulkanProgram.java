package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.minecraft.resources.Identifier;

/**
 * One shader pack screen pass compiled for RenderPearl's Vulkan backend: Iris-transformed GLSL is translated
 * ({@link IrisVulkanGlslTranslator}), registered with a {@link ShaderSource} before compilation starts, and compiled
 * with a {@link BindGroupLayout}, vertex format and colour targets derived from the translated interface.
 */
final class IrisVulkanProgram implements AutoCloseable {
	/** Vertex format of the full-screen quad; matches the {@code Position}/{@code UV0} inputs of Iris composite shaders. */
	static final VertexFormat QUAD_FORMAT = DefaultVertexFormat.POSITION_TEX;

	final String name;
	final IrisVulkanGlslTranslator.Result shaderInterface;
	final CompiledRenderPipeline pipeline;

	private IrisVulkanProgram(String name, IrisVulkanGlslTranslator.Result shaderInterface, CompiledRenderPipeline pipeline) {
		this.name = name;
		this.shaderInterface = shaderInterface;
		this.pipeline = pipeline;
	}

	/**
	 * @param transformed {@code TransformPatcher} output for the pass
	 * @param attachmentFormats format of colour attachment {@code i}, i.e. of the target fragment output location {@code i} writes
	 * @throws IrisVulkanGlslTranslator.TranslationException if the shaders use something RenderPearl cannot express
	 * @throws IllegalStateException if RenderPearl rejects the pipeline (details are in the log, from PipelineBuilder)
	 */
	static IrisVulkanProgram compile(String packKey, String name, Map<PatchShaderType, String> transformed, Map<String, String> suppliedUniforms, List<GpuFormat> attachmentFormats) {
		IrisVulkanGlslTranslator.Result result = prepare(name, transformed, suppliedUniforms);

		String path = "vulkan/" + packKey + "/" + name.toLowerCase(Locale.ROOT);
		Identifier vertexId = Identifier.fromNamespaceAndPath("iris", path + ".vsh");
		Identifier fragmentId = Identifier.fromNamespaceAndPath("iris", path + ".fsh");

		BindGroupLayout.Builder layout = BindGroupLayout.builder();
		for (String sampler : result.samplers()) {
			layout.withUniform(sampler, UniformType.COMBINED_IMAGE_SAMPLER);
		}
		if (!result.uniformFields().isEmpty()) {
			layout.withUniform(IrisVulkanGlslTranslator.UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER);
		}

		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath("iris", path))
			.withVertexShader(vertexId)
			.withFragmentShader(fragmentId)
			.withVertexBinding(0, QUAD_FORMAT)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withBindGroupLayout(layout.build())
			.withDepthStencilState(Optional.empty());
		for (int i = 0; i < attachmentFormats.size(); i++) {
			builder.withColorTargetState(i, new ColorTargetState(Optional.empty(), attachmentFormats.get(i), ColorTargetState.WRITE_ALL));
		}

		// Sources are registered before compilation is requested; Runnable::run compiles on this thread so failures
		// surface here instead of on first use.
		CompiledRenderPipeline compiled;
		try (TranslatedSource source = new TranslatedSource(vertexId, result.vertex(), fragmentId, result.fragment())) {
			compiled = RenderSystem.getDevice().compilePipeline(builder.build(), source, Runnable::run).join().finishCompile();
		}

		if (compiled == null) {
			throw new IllegalStateException("RenderPearl rejected the Vulkan pipeline for " + name + "; see the PipelineBuilder error above in latest.log");
		}

		return new IrisVulkanProgram(name, result, compiled);
	}

	/**
	 * Everything before GPU work: rejects stages RenderPearl lacks, translates, and checks the vertex inputs against
	 * the quad format. Needs no device, so the offline vulkanShaderCheck task runs this same code.
	 */
	static IrisVulkanGlslTranslator.Result prepare(String name, Map<PatchShaderType, String> transformed, Map<String, String> suppliedUniforms) {
		for (PatchShaderType stage : List.of(PatchShaderType.GEOMETRY, PatchShaderType.TESS_CONTROL, PatchShaderType.TESS_EVAL, PatchShaderType.COMPUTE)) {
			if (transformed.get(stage) != null) {
				throw new IrisVulkanGlslTranslator.TranslationException(name, stage.name().toLowerCase(Locale.ROOT) + " shaders are not supported by RenderPearl");
			}
		}

		IrisVulkanGlslTranslator.Result result = IrisVulkanGlslTranslator.translate(
			name, transformed.get(PatchShaderType.VERTEX), transformed.get(PatchShaderType.FRAGMENT), suppliedUniforms
		);

		for (IrisVulkanGlslTranslator.VertexInput input : result.vertexInputs()) {
			if (!QUAD_FORMAT.contains(input.name())) {
				throw new IrisVulkanGlslTranslator.TranslationException(name, "vertex input " + input.name() + " is not provided by the full-screen quad (Position, UV0)");
			}
		}

		return result;
	}

	@Override
	public void close() {
		pipeline.close();
	}

	record TranslatedSource(Identifier vertexId, String vertex, Identifier fragmentId, String fragment) implements ShaderSource {
		@Override
		public String getShader(Identifier id, ShaderType type) {
			if (type == ShaderType.VERTEX && id.equals(vertexId)) {
				return vertex;
			}

			if (type == ShaderType.FRAGMENT && id.equals(fragmentId)) {
				return fragment;
			}

			return null;
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
