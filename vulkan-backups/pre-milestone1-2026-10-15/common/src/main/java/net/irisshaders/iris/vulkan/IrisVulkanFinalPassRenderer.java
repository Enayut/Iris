package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import java.util.Optional;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;

/**
 * Phase 5: executes the shaderpack {@code final} pass into Minecraft's main
 * render target through a native Vulkan render pass — the Vulkan analogue of
 * {@code FinalPassRenderer}.
 */
final class IrisVulkanFinalPassRenderer {
	private IrisVulkanScreenPass finalPass;

	void setFinalPass(IrisVulkanScreenPass finalPass) {
		this.finalPass = finalPass;
	}

	void renderFinalPass(IrisVulkanGbufferTargets targets, VulkanShadowTargets shadow, IrisVulkanUniformSnapshot uniforms) {
		if (finalPass == null) {
			return;
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		var mainTarget = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainRenderTarget();

		try (RenderPass renderPass = encoder.createRenderPass(
			() -> "Iris/Vulkan: final pass",
			mainTarget.getColorTextureView(),
			Optional.empty()
		)) {
			renderPass.setPipeline(finalPass.compiled());
			IrisVulkanRenderPassBindings bindings = IrisVulkanRenderPassBindings.forStage(
				targets, shadow, new it.unimi.dsi.fastutil.ints.IntOpenHashSet(), new it.unimi.dsi.fastutil.ints.IntOpenHashSet()
			);
			bindings.apply(renderPass);
			renderPass.setUniform("iris_Uniforms", uniforms.slice());
			renderPass.setIndexBuffer(IrisVulkanScreenPassExecutor.quadIndices(), IndexType.SHORT);
			renderPass.setVertexBuffer(0, FullScreenQuadRenderer.INSTANCE.getQuad().slice());
			renderPass.drawIndexed(6, 1, 0, 0, 0);
		}
	}
}
