package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.minecraft.resources.Identifier;

/**
 * Phase 2+5: compiles Iris shaderpack screen passes (composite/deferred/
 * prepare/begin) into Vulkan pipelines. For each valid pass source the planner
 * runs Iris's existing {@code TransformPatcher} via the orchestrator's
 * transformer callback (preserving shaderpack semantics), registers the
 * transformed GLSL in a shared {@link IrisVulkanShaderSourceMap}, declares a
 * renderpearl {@link RenderPipeline} referencing that GLSL, and submits it to
 * {@code GpuDevice#compilePipeline} — which under the VulkanBackend compiles
 * GLSL→SPIR-V and creates the {@code VkPipeline}. Compilation runs async; the
 * executor blocks only on first use of a pass.
 */
final class IrisVulkanScreenPassPlanner {
	private final IrisVulkanShaderSourceMap sourceMap;
	private final List<CompiledRenderPipeline> compiledForTracking = new ArrayList<>();

	IrisVulkanScreenPassPlanner(IrisVulkanShaderSourceMap sourceMap) {
		this.sourceMap = sourceMap;
	}

	/** Returns null when the shaderpack declares no valid pass for this stage. */
	IrisVulkanScreenPassGraph plan(
		String stageName,
		ProgramSource[] sources,
		IrisVulkanRenderer.SourceTransformer transformer
	) {
		IrisVulkanScreenPassGraph graph = new IrisVulkanScreenPassGraph();
		Map<IrisVulkanShaderSourceMap.SourceKey, String> registered = new HashMap<>();

		for (ProgramSource source : sources) {
			if (source == null || !source.isValid()) {
				continue;
			}

			Map<PatchShaderType, String> transformed = transformer.transform(source);
			if (transformed == null
				|| transformed.get(PatchShaderType.VERTEX) == null
				|| transformed.get(PatchShaderType.FRAGMENT) == null) {
				continue;
			}

			String base = "vulkan/" + stageName.toLowerCase(Locale.ROOT) + "/" + source.getName();
			Identifier vertexId = Identifier.fromNamespaceAndPath("iris", base + ".vsh");
			Identifier fragmentId = Identifier.fromNamespaceAndPath("iris", base + ".fsh");
			registered.put(
				new IrisVulkanShaderSourceMap.SourceKey(vertexId, com.mojang.renderpearl.api.pipeline.ShaderType.VERTEX),
				transformed.get(PatchShaderType.VERTEX)
			);
			registered.put(
				new IrisVulkanShaderSourceMap.SourceKey(fragmentId, com.mojang.renderpearl.api.pipeline.ShaderType.FRAGMENT),
				transformed.get(PatchShaderType.FRAGMENT)
			);

			IntSet reads = new IntOpenHashSet();
			IntSet writes = new IntOpenHashSet();
			int[] drawBuffers = source.getDirectives().getDrawBuffers();
			if (drawBuffers != null) {
				for (int buffer : drawBuffers) {
					if (buffer >= 0 && buffer < 16) {
						writes.add(buffer);
					}
				}
			}
			String fragment = transformed.get(PatchShaderType.FRAGMENT);
			for (int c = 0; c < 16; c++) {
				if (fragment.contains("colortex" + c)) {
					reads.add(c);
				}
			}

			RenderPipeline pipeline = buildPipeline(stageName, vertexId, fragmentId);
			CompletableFuture<CompiledRenderPipeline.Pending> future =
				RenderSystem.getDevice().compilePipeline(pipeline, sourceMap, Runnable::run);

			IrisVulkanScreenPass pass = new IrisVulkanScreenPass(source.getName(), pipeline, future);
			graph.add(pass, reads, writes);
			Iris.logger.info("[Iris/Vulkan] Compiled Vulkan screen pass '{}' ({}).", source.getName(), stageName);
		}

		if (!registered.isEmpty()) {
			sourceMap.registerAll(registered);
		}

		return graph.size() == 0 ? null : graph;
	}

	private RenderPipeline buildPipeline(String stageName, Identifier vertexId, Identifier fragmentId) {
		return RenderPipeline.builder(new Snippet[0])
			.withLocation(Identifier.fromNamespaceAndPath("iris", "vulkan/" + stageName.toLowerCase(Locale.ROOT)))
			.withVertexShader(vertexId)
			.withFragmentShader(fragmentId)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.withColorTargetState(ColorTargetState.DEFAULT)
			.build();
	}

	void track(CompiledRenderPipeline pipeline) {
		compiledForTracking.add(pipeline);
	}

	void destroy() {
		for (CompiledRenderPipeline pipeline : compiledForTracking) {
			pipeline.close();
		}

		compiledForTracking.clear();
	}
}
