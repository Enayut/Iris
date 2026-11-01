package net.irisshaders.iris.vulkan;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.HashMap;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.gl.texture.TextureType;

/**
 * Phase 1-5 orchestrator: owns the Vulkan-native screen-pass chain
 * (targets, shaders, passes, executor) and drives it alongside the existing
 * OpenGL pipeline when running on Minecraft 26.3's native Vulkan backend.
 *
 * <p>Lifecycle: {@link #initialize} when a shaderpack loads on a Vulkan
 * device; {@link #compilePasses} compiles a stage's shaderpack passes for
 * Vulkan; {@link #executeFrameStage} runs the pass graph at each composite
 * stage; {@link #renderFinalPass} writes the last image out; {@link #destroy}
 * releases all Vulkan resources.</p>
 */
public final class IrisVulkanRenderer {
	/** Callback that runs Iris's TransformPatcher for a pass source. */
	public interface SourceTransformer {
		Map<PatchShaderType, String> transform(ProgramSource source);
	}

	private IrisVulkanGbufferTargets targets;
	private VulkanShadowTargets shadowTargets;
	private IrisVulkanUniformSnapshot uniforms;
	private IrisVulkanShaderSourceMap sourceMap;
	private IrisVulkanScreenPassPlanner planner;
	private IrisVulkanScreenPassGraph deferredGraph;
	private IrisVulkanScreenPassGraph compositeGraph;
	private final IrisVulkanScreenPassExecutor executor = new IrisVulkanScreenPassExecutor();
	private final IrisVulkanFinalPassRenderer finalPassRenderer = new IrisVulkanFinalPassRenderer();
	private boolean initialized;

	private static final IrisVulkanRenderer INSTANCE = new IrisVulkanRenderer();

	public static IrisVulkanRenderer get() {
		return INSTANCE;
	}

	/** {@code true} when the Vulkan chain is active and owns screen passes. */
	public boolean isActive() {
		return initialized;
	}

	public void initialize(int width, int height) {
		if (initialized) {
			destroy();
		}

		Iris.logger.info("[Iris/Vulkan] Initializing Vulkan screen-pass chain ({}x{}).", width, height);
		targets = new IrisVulkanGbufferTargets(width, height);
		shadowTargets = new VulkanShadowTargets(1024, 1024);
		uniforms = new IrisVulkanUniformSnapshot();
		uniforms.registerCommonUniforms();
		sourceMap = new IrisVulkanShaderSourceMap(new HashMap<>());
		planner = new IrisVulkanScreenPassPlanner(sourceMap);
		initialized = true;
		Iris.logger.info("[Iris/Vulkan] Vulkan screen-pass chain initialized: colortex storage, shadow targets and uniform block allocated on the Vulkan device.");
	}

	/** Compiles a shaderpack stage's passes for Vulkan using Iris's transformer. */
	public void compilePasses(CompositePass stage, ProgramSource[] sources, SourceTransformer transformer) {
		if (!initialized || sources == null) {
			return;
		}

		IrisVulkanScreenPassGraph graph = planner.plan(stage.name(), sources, transformer);

		switch (stage) {
			case DEFERRED -> deferredGraph = graph;
			case COMPOSITE -> compositeGraph = graph;
			default -> {
			}
		}
	}

	/** Convenience overload used by the pipeline with the standard patcher. */
	public void compilePasses(
		CompositePass stage,
		ProgramSource[] sources,
		TextureStage textureStage,
		Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
		java.util.Set<String> textureOverrides
	) {
		compilePasses(stage, sources, source -> TransformPatcher.patchComposite(
			source.getName(),
			source.getVertexSource().orElse(null),
			source.getGeometrySource().orElse(null),
			source.getFragmentSource().orElse(null),
			textureStage,
			textureMap,
			textureOverrides
		));
	}

	public void executeFrameStage(CompositePass stage) {
		if (!initialized) {
			return;
		}

		IrisVulkanScreenPassGraph graph = switch (stage) {
			case DEFERRED -> deferredGraph;
			case COMPOSITE -> compositeGraph;
			default -> null;
		};

		uniforms.update();
		executor.execute(graph, targets, shadowTargets, uniforms);
	}

	public void renderFinalPass() {
		if (initialized) {
			finalPassRenderer.renderFinalPass(targets, shadowTargets, uniforms);
		}
	}

	public void resize(int newWidth, int newHeight) {
		if (initialized && targets != null) {
			targets.resize(newWidth, newHeight);
		}
	}

	public void destroy() {
		if (!initialized) {
			return;
		}

		initialized = false;
		destroyGraph(deferredGraph);
		destroyGraph(compositeGraph);
		deferredGraph = null;
		compositeGraph = null;

		if (planner != null) {
			planner.destroy();
			planner = null;
		}

		if (uniforms != null) {
			uniforms.destroy();
			uniforms = null;
		}

		if (shadowTargets != null) {
			shadowTargets.destroy();
			shadowTargets = null;
		}

		if (targets != null) {
			targets.destroy();
			targets = null;
		}

		Iris.logger.info("[Iris/Vulkan] Vulkan screen-pass chain destroyed.");
	}

	private static void destroyGraph(IrisVulkanScreenPassGraph graph) {
		if (graph == null) {
			return;
		}

		for (IrisVulkanScreenPassGraph.Node node : graph.nodes()) {
			node.pass.destroy();
		}
	}

	private IrisVulkanRenderer() {
	}
}
