package net.irisshaders.iris.vulkan;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.Identifier;

/**
 * Phase 5: one screen pass (composite/deferred/prepare/begin/final) compiled
 * for the Vulkan backend. Holds the declarative {@link RenderPipeline} (which
 * carries the vertex format, bind group layout, blend and depth state), the
 * async compile future from {@code GpuDevice#compilePipeline}, and the packed
 * {@code CompiledRenderPipeline} once compilation finishes.
 *
 * <p>The pipeline declaration references the transformed shaderpack GLSL by
 * identifier; {@link IrisVulkanShaderSourceMap} supplies the actual source when
 * the backend compiler asks for it.</p>
 */
final class IrisVulkanScreenPass {
	private final String name;
	private final RenderPipeline pipeline;
	private final CompletableFuture<CompiledRenderPipeline.Pending> pending;
	private volatile CompiledRenderPipeline compiled;

	IrisVulkanScreenPass(String name, RenderPipeline pipeline, CompletableFuture<CompiledRenderPipeline.Pending> pending) {
		this.name = name;
		this.pipeline = pipeline;
		this.pending = pending;
	}

	String name() {
		return name;
	}

	RenderPipeline pipeline() {
		return pipeline;
	}

	/** Returns the compiled pipeline, blocking on first use only. */
	CompiledRenderPipeline compiled() {
		CompiledRenderPipeline c = compiled;
		if (c == null) {
			synchronized (this) {
				if (compiled == null) {
					compiled = pending.join().finishCompile();
				}
				c = compiled;
			}
		}

		return c;
	}

	void destroy() {
		CompiledRenderPipeline c = compiled;
		if (c != null) {
			c.close();
			compiled = null;
		}
	}
}
