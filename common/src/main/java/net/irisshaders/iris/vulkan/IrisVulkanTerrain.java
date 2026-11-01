package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTests;
import net.irisshaders.iris.gl.blending.BlendMode;
import net.irisshaders.iris.gl.blending.BlendModeFunction;
import net.irisshaders.iris.gl.blending.BufferBlendInformation;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Milestone 2: Sodium's terrain drawn with the pack's programs into the pack's colour targets: the opaque layers
 * (solid and cutout) with {@code gbuffers_terrain}, and the translucent layer with {@code gbuffers_water}.
 *
 * <p>The programs are Iris's own Sodium transformation of the pack sources (as ShaderCreator does for
 * {@code Patch.SODIUM}), translated for RenderPearl with Sodium's {@code u_Globals} block, push constants and
 * section fade buffer kept, so Sodium's own draw loop (DefaultChunkRenderer#render) feeds them unchanged. The
 * VKOnly mixin on DefaultChunkRenderer swaps the pipeline while {@link #active} is set; everything else about the
 * draw (index buffer, per-region push constants, multi-draw batches) stays Sodium's.</p>
 */
public final class IrisVulkanTerrain {
	static final String PUSH_CONSTANT_BLOCK = "iris_SodiumPushConstants";
	/** Sodium's per-region push constants: vec3 region offset, int current time, uint region id (DefaultChunkRenderer). */
	static final int PUSH_CONSTANT_SIZE = 20;
	/** Sodium's own resources that its draw loop binds by these names. */
	private static final String SODIUM_GLOBALS = "u_Globals";
	private static final String SODIUM_SECTION_TIME_INFO = "u_SectionTimeInfo";

	private static final Matrix4f modelViewInverse = new Matrix4f();
	private static final Matrix3f normalMatrix = new Matrix3f();
	private static WorldRenderingPhase phase = WorldRenderingPhase.NONE;
	private static Active active;

	private IrisVulkanTerrain() {
	}

	/** Sodium's terrain layers, as Iris's SODIUM_TERRAIN_* shader keys. */
	public enum Layer {
		SOLID(ProgramId.TerrainSolid, AlphaTests.OFF, WorldRenderingPhase.TERRAIN_SOLID),
		CUTOUT(ProgramId.TerrainCutout, AlphaTests.HALF_ALPHA, WorldRenderingPhase.TERRAIN_CUTOUT),
		TRANSLUCENT(ProgramId.Water, AlphaTests.ONE_TENTH_ALPHA, WorldRenderingPhase.TERRAIN_TRANSLUCENT);

		public final ProgramId programId;
		final AlphaTest fallbackAlpha;
		final WorldRenderingPhase phase;

		Layer(ProgramId programId, AlphaTest fallbackAlpha, WorldRenderingPhase phase) {
			this.programId = programId;
			this.fallbackAlpha = fallbackAlpha;
			this.phase = phase;
		}

		TerrainRenderPass sodiumPass() {
			return switch (this) {
				case SOLID -> DefaultTerrainRenderPasses.SOLID;
				case CUTOUT -> DefaultTerrainRenderPasses.CUTOUT;
				case TRANSLUCENT -> DefaultTerrainRenderPasses.TRANSLUCENT;
			};
		}

		boolean isTranslucent() {
			return this == TRANSLUCENT;
		}
	}

	/** One compiled terrain program and the colour targets its fragment outputs write, in output order. */
	record Program(Layer layer, String name, IrisVulkanGlslTranslator.Result shaderInterface, CompiledRenderPipeline pipeline, int[] drawBuffers, float alphaReference)
		implements AutoCloseable {
		@Override
		public void close() {
			pipeline.close();
		}
	}

	/** The program Sodium's draw loop should use for {@code pass}, and what to bind once the pipeline is set. */
	private record Active(TerrainRenderPass pass, Program program, Consumer<RenderPass> binder) {
	}

	/** Iris's Sodium transformation (as ShaderCreator does for Patch.SODIUM), with the pack's gbuffer texture names. */
	static Map<PatchShaderType, String> transform(PackDirectives directives, ProgramSource source, Layer layer) {
		Set<String> overrides = directives.getTextureMap().keySet().stream()
			.filter(key -> key.third() == TextureStage.GBUFFERS_AND_SHADOW)
			.map(key -> key.first())
			.collect(Collectors.toSet());
		return TransformPatcher.patchSodium(
			source.getName(),
			source.getVertexSource().orElseThrow(),
			source.getGeometrySource().orElse(null),
			source.getTessControlSource().orElse(null),
			source.getTessEvalSource().orElse(null),
			source.getFragmentSource().orElseThrow(),
			alphaTest(source, layer),
			directives.getTextureMap(),
			overrides,
			false
		);
	}

	static AlphaTest alphaTest(ProgramSource source, Layer layer) {
		return source.getDirectives().getAlphaTestOverride().orElse(layer.fallbackAlpha);
	}

	/**
	 * Everything before GPU work: rejects stages RenderPearl lacks, translates, and checks the vertex inputs and
	 * Sodium resources against what Sodium's draw loop provides. The offline vulkanShaderCheck runs this same code.
	 */
	static IrisVulkanGlslTranslator.Result prepare(String name, Map<PatchShaderType, String> transformed, Map<String, String> suppliedUniforms, VertexFormat vertexFormat) {
		for (PatchShaderType stage : List.of(PatchShaderType.GEOMETRY, PatchShaderType.TESS_CONTROL, PatchShaderType.TESS_EVAL, PatchShaderType.COMPUTE)) {
			if (transformed.get(stage) != null) {
				throw new IrisVulkanGlslTranslator.TranslationException(name, stage.name().toLowerCase(Locale.ROOT) + " shaders are not supported by RenderPearl");
			}
		}

		IrisVulkanGlslTranslator.Result result = IrisVulkanGlslTranslator.translate(
			name, transformed.get(PatchShaderType.VERTEX), transformed.get(PatchShaderType.FRAGMENT), suppliedUniforms, PUSH_CONSTANT_BLOCK
		);

		for (IrisVulkanGlslTranslator.VertexInput input : result.vertexInputs()) {
			if (!vertexFormat.contains(input.name())) {
				throw new IrisVulkanGlslTranslator.TranslationException(name, "vertex input " + input.name() + " is not in Iris's Sodium terrain vertex format");
			}
		}
		for (String block : result.uniformBlocks()) {
			if (!block.equals(SODIUM_GLOBALS)) {
				throw new IrisVulkanGlslTranslator.TranslationException(name, "uniform block " + block + " is not provided on Vulkan");
			}
		}
		for (String buffer : result.texelBuffers()) {
			if (!buffer.equals(SODIUM_SECTION_TIME_INFO)) {
				throw new IrisVulkanGlslTranslator.TranslationException(name, "buffer sampler " + buffer + " is not provided on Vulkan");
			}
		}

		return result;
	}

	/** {@code formats} are the formats of the colour targets {@code drawBuffers} names, in the same order. */
	static Program compile(
		String packKey, PackDirectives directives, ProgramSource source, Layer layer, Map<String, String> suppliedUniforms, VertexFormat vertexFormat,
		int[] drawBuffers, List<GpuFormat> formats
	) {
		String name = source.getName();
		IrisVulkanGlslTranslator.Result result = prepare(name, transform(directives, source, layer), suppliedUniforms, vertexFormat);
		List<Optional<BlendFunction>> blending = blending(source.getDirectives(), layer, drawBuffers, formats);
		return build(packKey, name, layer, result, vertexFormat, drawBuffers, formats, blending, alphaTest(source, layer).reference());
	}

	/**
	 * Per colour target: Sodium's own blending for the layer (translucent blending for the translucent layer, none
	 * otherwise, as ShaderChunkRenderer#createShader), replaced by the pack's {@code blend.<program>} and then its
	 * {@code blend.<program>.<buffer>} directives. Integer targets cannot blend.
	 */
	static List<Optional<BlendFunction>> blending(ProgramDirectives directives, Layer layer, int[] drawBuffers, List<GpuFormat> formats) {
		Optional<BlendFunction> program = directives.getBlendModeOverride()
			.map(override -> toBlendFunction(override.getBlendMode()))
			.orElse(layer.isTranslucent() ? Optional.of(BlendFunction.TRANSLUCENT) : Optional.empty());
		List<Optional<BlendFunction>> blending = new ArrayList<>();
		for (int i = 0; i < drawBuffers.length; i++) {
			Optional<BlendFunction> target = program;
			for (BufferBlendInformation buffer : directives.getBufferBlendOverrides()) {
				if (buffer.index() == drawBuffers[i]) {
					target = toBlendFunction(buffer.blendMode());
				}
			}
			blending.add(IrisVulkanFormats.isInteger(formats.get(i)) ? Optional.empty() : target);
		}
		return blending;
	}

	/** Iris's parsed blend mode (OpenGL factor ids) as RenderPearl's, or empty for {@code off} (null). */
	static Optional<BlendFunction> toBlendFunction(BlendMode mode) {
		if (mode == null) {
			return Optional.empty();
		}
		return Optional.of(new BlendFunction(factor(mode.srcRgb()), factor(mode.dstRgb()), factor(mode.srcAlpha()), factor(mode.dstAlpha())));
	}

	private static BlendFactor factor(int glId) {
		for (BlendModeFunction function : BlendModeFunction.values()) {
			if (function.getGlId() == glId) {
				return BlendFactor.valueOf(function.name());
			}
		}
		throw new IllegalArgumentException("Unknown blend factor " + glId);
	}

	private static Program build(
		String packKey, String name, Layer layer, IrisVulkanGlslTranslator.Result result, VertexFormat vertexFormat, int[] drawBuffers, List<GpuFormat> formats,
		List<Optional<BlendFunction>> blending, float alphaReference
	) {
		if (result.fragmentOutputs().size() > drawBuffers.length) {
			throw new IrisVulkanGlslTranslator.TranslationException(name, "fragment outputs " + result.fragmentOutputs() + " exceed the draw buffers " + java.util.Arrays.toString(drawBuffers));
		}

		String path = "vulkan/" + packKey + "/" + name.toLowerCase(Locale.ROOT) + "_" + layer.name().toLowerCase(Locale.ROOT);
		Identifier vertexId = Identifier.fromNamespaceAndPath("iris", path + ".vsh");
		Identifier fragmentId = Identifier.fromNamespaceAndPath("iris", path + ".fsh");

		BindGroupLayout.Builder layout = BindGroupLayout.builder();
		for (String sampler : result.samplers()) {
			layout.withUniform(sampler, UniformType.COMBINED_IMAGE_SAMPLER);
		}
		for (String buffer : result.texelBuffers()) {
			layout.withUniform(buffer, UniformType.TEXEL_BUFFER, GpuFormat.R32_SINT);
		}
		for (String block : result.uniformBlocks()) {
			layout.withUniform(block, UniformType.UNIFORM_BUFFER);
		}
		if (!result.uniformFields().isEmpty()) {
			layout.withUniform(IrisVulkanGlslTranslator.UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER);
		}

		// Sodium pushes its 20 bytes for every region whether or not the pack's shader reads them.
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath("iris", path))
			.withVertexShader(vertexId)
			.withFragmentShader(fragmentId)
			.withBindGroupLayout(layout.build())
			.withPushConstantSize(PUSH_CONSTANT_SIZE)
			.withCull(true)
			.withDepthStencilState(DepthStencilState.DEFAULT)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withVertexBinding(0, vertexFormat);
		for (int i = 0; i < formats.size(); i++) {
			builder.withColorTargetState(i, new ColorTargetState(blending.get(i), formats.get(i), ColorTargetState.WRITE_ALL));
		}

		CompiledRenderPipeline compiled;
		try (IrisVulkanProgram.TranslatedSource sources = new IrisVulkanProgram.TranslatedSource(vertexId, result.vertex(), fragmentId, result.fragment())) {
			compiled = RenderSystem.getDevice().compilePipeline(builder.build(), sources, Runnable::run).join().finishCompile();
		}

		if (compiled == null) {
			throw new IllegalStateException("RenderPearl rejected the Vulkan terrain pipeline for " + name + "; see the PipelineBuilder error above in latest.log");
		}

		return new Program(layer, name, result, compiled, drawBuffers, alphaReference);
	}

	/**
	 * Uniforms only terrain programs read: the matrices SodiumTransformer substitutes for the built-in ones (computed
	 * from Sodium's model-view, as ExtendedShader does from the current one) and the world rendering phase.
	 */
	static void addUniforms(IrisVulkanUniforms uniforms) {
		BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniforms);
		uniforms.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ModelViewMatInverse", () -> modelViewInverse);
		uniforms.uniformMatrix3(UniformUpdateFrequency.PER_FRAME, "iris_NormalMat", () -> normalMatrix);
		uniforms.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ProjMatInverse",
			() -> new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection()).invert());
		// Replaces the screen-pass registration (WorldRenderingPhase.NONE); later registrations win.
		uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "renderStage", () -> phase.ordinal());
	}

	/** Sets the per-layer uniform state before the layer's uniform block is written. */
	static void prepareUniforms(Program program, Matrix4fc modelView) {
		modelView.invert(modelViewInverse);
		modelViewInverse.transpose3x3(normalMatrix);
		phase = program.layer.phase;
		CapturedRenderingState.INSTANCE.setCurrentAlphaTest(program.alphaReference);
	}

	static void begin(Program program, Consumer<RenderPass> binder) {
		active = new Active(program.layer.sodiumPass(), program, binder);
	}

	static void end() {
		active = null;
		phase = WorldRenderingPhase.NONE;
		CapturedRenderingState.INSTANCE.setCurrentAlphaTest(0.0f);
	}

	/**
	 * Called by the VKOnly mixin in place of Sodium's {@code pass.setPipeline}: binds the pack's program and its
	 * resources when Iris is drawing this terrain pass.
	 *
	 * @return false when Sodium should use its own pipeline
	 */
	public static boolean setPipeline(RenderPass renderPass, TerrainRenderPass sodiumPass) {
		Active current = active;
		if (current == null || current.pass != sodiumPass) {
			return false;
		}

		renderPass.setPipeline(current.program.pipeline);
		current.binder.accept(renderPass);
		return true;
	}

	public static boolean isDrawing() {
		return active != null;
	}
}
