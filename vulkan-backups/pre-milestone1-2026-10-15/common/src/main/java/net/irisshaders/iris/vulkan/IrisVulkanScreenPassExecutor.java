package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.IndexType;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Optional;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;

/**
 * Phase 5: executes the Vulkan screen-pass graph on the renderpearl command
 * encoder. Each graph node becomes a real native render pass
 * ({@code VkRenderPass} + {@code vkCmdDraw} under the VulkanBackend):
 *
 * <ol>
 *   <li>{@code createRenderPass} with the pass's colortex color attachment</li>
 *   <li>{@code setPipeline} with the SPIR-V compiled pipeline</li>
 *   <li>colortex/shadow sampler bindings via {@link IrisVulkanRenderPassBindings}</li>
 *   <li>fullscreen-quad indexed draw</li>
 * </ol>
 *
 * <p>Flip semantics mirror the OpenGL chain: a pass's writes become the next
 * pass's reads via the main/alt swap in {@link IrisVulkanTarget}.</p>
 */
final class IrisVulkanScreenPassExecutor {
	private static GpuBuffer quadIndices;

	void execute(
		IrisVulkanScreenPassGraph graph,
		IrisVulkanGbufferTargets targets,
		VulkanShadowTargets shadow,
		IrisVulkanUniformSnapshot uniforms
	) {
		if (graph == null) {
			return;
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		IntSet flippedAfterLast = new IntOpenHashSet();

		for (IrisVulkanScreenPassGraph.Node node : graph.nodes()) {
			IrisVulkanScreenPass pass = node.pass;
			IrisVulkanTarget colorTarget = firstWrittenTarget(targets, node);

			RenderPassDescriptor.Builder descriptor = RenderPassDescriptor.builder(() -> "Iris/Vulkan: " + pass.name());
			if (colorTarget != null) {
				descriptor.withColorAttachment(colorTarget.mainView(), Optional.of(new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.0f)));
			} else {
				descriptor.withColorAttachment(targets.get(0).mainView(), Optional.of(new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.0f)));
			}

			try (RenderPass renderPass = encoder.createRenderPass(descriptor.build())) {
				renderPass.setPipeline(pass.compiled());
				IrisVulkanRenderPassBindings bindings = IrisVulkanRenderPassBindings.forStage(
					targets, shadow, flippedAfterLast, node.reads
				);
				bindings.apply(renderPass);
				renderPass.setUniform("iris_Uniforms", uniforms.slice());
				renderPass.setIndexBuffer(quadIndices(), IndexType.SHORT);
				renderPass.setVertexBuffer(0, FullScreenQuadRenderer.INSTANCE.getQuad().slice());
				renderPass.drawIndexed(6, 1, 0, 0, 0);
			}

			// Flip the written targets so subsequent passes read what we wrote.
			for (int write : node.writes) {
				IrisVulkanTarget target = targets.get(write);
				if (target != null) {
					target.swap();
					flippedAfterLast.add(write);
				}
			}
		}

		encoder.submit();
	}

	private static IrisVulkanTarget firstWrittenTarget(IrisVulkanGbufferTargets targets, IrisVulkanScreenPassGraph.Node node) {
		for (int write : node.writes) {
			IrisVulkanTarget target = targets.get(write);
			if (target != null) {
				return target;
			}
		}

		return null;
	}

	static GpuBuffer quadIndices() {
		if (quadIndices == null) {
			java.nio.ByteBuffer data = java.nio.ByteBuffer.allocateDirect(12)
				.order(java.nio.ByteOrder.nativeOrder())
				.putShort((short) 0).putShort((short) 1).putShort((short) 2)
				.putShort((short) 2).putShort((short) 3).putShort((short) 0);
			data.flip();
			quadIndices = RenderSystem.getDevice().createBuffer(
				() -> "Iris Vulkan quad indices",
				GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
				12
			);
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(quadIndices.slice(0, 12), data);
		}

		return quadIndices;
	}
}
