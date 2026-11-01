package net.irisshaders.iris.vulkan;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 5: binding resolution for one Vulkan screen pass. Maps Iris sampler
 * names ({@code colortex0..15}, {@code gdepth}, {@code shadowtex0/1}, ...)
 * onto concrete {@link GpuTextureView} + {@link GpuSampler} pairs honoring
 * flip state, so {@link IrisVulkanScreenPassExecutor} can issue
 * {@code RenderPass#setUniform} calls before drawing.
 */
final class IrisVulkanRenderPassBindings {
	private final Map<String, Binding> bindings = new LinkedHashMap<>();

	private IrisVulkanRenderPassBindings() {
	}

	static IrisVulkanRenderPassBindings forStage(
		IrisVulkanGbufferTargets targets,
		VulkanShadowTargets shadow,
		IntSet flipped,
		IntSet stageReadsFromAlt
	) {
		IrisVulkanRenderPassBindings bindings = new IrisVulkanRenderPassBindings();

		for (int i = 0; i < 16; i++) {
			IrisVulkanTarget target = targets.get(i);
			if (target == null) {
				continue;
			}

			boolean readAlt = flipped.contains(i) ^ stageReadsFromAlt.contains(i);
			GpuTextureView view = readAlt ? target.altView() : target.mainView();
			GpuSampler sampler = readAlt ? target.altSampler() : target.mainSampler();
			bindings.bindings.put("colortex" + i, new Binding(view, sampler));
			bindings.bindings.put("colortex" + i + "Alt", new Binding(readAlt ? target.mainView() : target.altView(), readAlt ? target.mainSampler() : target.altSampler()));

			if (i == 0) {
				bindings.bindings.put("gcolor", bindings.bindings.get("colortex0"));
			}

			if (i == 1) {
				bindings.bindings.put("gdepth", bindings.bindings.get("colortex1"));
			}
		}

		if (shadow != null) {
			bindings.bindings.put("shadowtex0", new Binding(shadow.depthView(), shadow.depthSampler()));
			bindings.bindings.put("shadowtex1", new Binding(shadow.depthView(), shadow.depthSampler()));
			bindings.bindings.put("shadowcolor0", new Binding(shadow.colorView(), shadow.colorSampler()));
			bindings.bindings.put("watershadow", bindings.bindings.get("shadowtex0"));
		}

		return bindings;
	}

	void apply(RenderPass pass) {
		for (Map.Entry<String, Binding> entry : bindings.entrySet()) {
			pass.setUniform(entry.getKey(), entry.getValue().view(), entry.getValue().sampler());
		}
	}

	record Binding(GpuTextureView view, GpuSampler sampler) {
	}
}
