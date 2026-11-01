package net.irisshaders.iris.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;

/**
 * Phase 3: Vulkan-native storage for all Iris colortex targets (colortex0-15).
 * Mirrors the layout information the OpenGL {@code RenderTargets} derives from
 * shaderpack directives, but allocates real {@code GpuTexture}s on the
 * renderpearl device (native {@code VkImage}s under the VulkanBackend).
 */
final class IrisVulkanGbufferTargets {
	private final List<IrisVulkanTarget> targets = new ArrayList<>();
	private com.mojang.renderpearl.api.textures.GpuTexture depthTexture;
	private com.mojang.renderpearl.api.textures.GpuTextureView depthView;
	private int width;
	private int height;

	IrisVulkanGbufferTargets(int width, int height) {
		this(width, height, null);
	}

	IrisVulkanGbufferTargets(int width, int height, PackDirectives packDirectives) {
		this.width = width;
		this.height = height;

		var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
		depthTexture = device.createTexture(
			() -> "iris vulkan depth",
			com.mojang.renderpearl.api.textures.GpuTexture.USAGE_RENDER_ATTACHMENT
				| com.mojang.renderpearl.api.textures.GpuTexture.USAGE_TEXTURE_BINDING
				| com.mojang.renderpearl.api.textures.GpuTexture.USAGE_COPY_SRC,
			GpuFormat.D32_FLOAT,
			width, height, 1, 1
		);
		depthView = device.createTextureView(depthTexture);

		for (int i = 0; i < 16; i++) {
			InternalTextureFormat format = InternalTextureFormat.RGBA8;
			int scaleW = width;
			int scaleH = height;

			if (packDirectives != null) {
				PackRenderTargetDirectives.RenderTargetSettings settings =
					packDirectives.getRenderTargetDirectives().getRenderTargetSettings().get(i);
				if (settings != null) {
					format = settings.getInternalFormat();
				}
			}

			// Texture-scale directives are not applied here yet; all targets
			// start at swapchain resolution and are resized with the frame.
			targets.add(IrisVulkanTarget.create(i, "colortex" + i, format, scaleW, scaleH));
		}
	}

	IrisVulkanTarget get(int index) {
		return index >= 0 && index < targets.size() ? targets.get(index) : null;
	}

	com.mojang.renderpearl.api.textures.GpuTexture depthTexture() {
		return depthTexture;
	}

	com.mojang.renderpearl.api.textures.GpuTextureView depthView() {
		return depthView;
	}

	int width() {
		return width;
	}

	int height() {
		return height;
	}

	void resize(int newWidth, int newHeight) {
		if (newWidth == width && newHeight == height) {
			return;
		}

		width = newWidth;
		height = newHeight;
		var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
		depthView.close();
		depthTexture.close();
		depthTexture = device.createTexture(
			() -> "iris vulkan depth",
			com.mojang.renderpearl.api.textures.GpuTexture.USAGE_RENDER_ATTACHMENT
				| com.mojang.renderpearl.api.textures.GpuTexture.USAGE_TEXTURE_BINDING
				| com.mojang.renderpearl.api.textures.GpuTexture.USAGE_COPY_SRC,
			GpuFormat.D32_FLOAT,
			width, height, 1, 1
		);
		depthView = device.createTextureView(depthTexture);

		for (IrisVulkanTarget target : targets) {
			target.resize(width, height);
		}
	}

	void destroy() {
		for (IrisVulkanTarget target : targets) {
			target.destroy();
		}

		targets.clear();
		depthView.close();
		depthTexture.close();
	}
}
