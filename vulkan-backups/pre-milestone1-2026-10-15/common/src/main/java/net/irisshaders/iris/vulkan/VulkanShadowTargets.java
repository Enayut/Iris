package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 3: the Vulkan shadow map pair (depth + color) backing the
 * {@code shadowtex} / {@code shadowcolor} sampler declarations.
 */
final class VulkanShadowTargets {
	private static final int SHADOW_DEPTH_USAGE =
		GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC;
	private static final int SHADOW_COLOR_USAGE =
		GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST;

	private final List<IrisVulkanTarget> colorTargets = new ArrayList<>();
	private GpuTexture depthTexture;
	private GpuTextureView depthView;
	private GpuTexture colorTexture;
	private GpuTextureView colorView;
	private int width;
	private int height;

	VulkanShadowTargets(int width, int height) {
		this.width = width;
		this.height = height;
		allocate();
	}

	private void allocate() {
		var device = RenderSystem.getDevice();
		depthTexture = device.createTexture(() -> "iris shadow depth", SHADOW_DEPTH_USAGE, com.mojang.renderpearl.api.GpuFormat.D32_FLOAT, width, height, 1, 1);
		depthView = device.createTextureView(depthTexture);
		colorTexture = device.createTexture(() -> "iris shadow color", SHADOW_COLOR_USAGE, com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, width, height, 1, 1);
		colorView = device.createTextureView(colorTexture);
	}

	void resize(int newWidth, int newHeight) {
		if (newWidth == width && newHeight == height) {
			return;
		}

		width = newWidth;
		height = newHeight;
		release();
		allocate();
	}

	GpuTexture depthTexture() {
		return depthTexture;
	}

	GpuTextureView depthView() {
		return depthView;
	}

	GpuTexture colorTexture() {
		return colorTexture;
	}

	GpuTextureView colorView() {
		return colorView;
	}

	GpuSampler depthSampler() {
		return VulkanSamplers.nearestClamp();
	}

	GpuSampler colorSampler() {
		return VulkanSamplers.linearClamp();
	}

	int width() {
		return width;
	}

	int height() {
		return height;
	}

	void destroy() {
		release();
	}

	private void release() {
		if (depthView != null) {
			depthView.close();
			depthView = null;
		}

		if (colorView != null) {
			colorView.close();
			colorView = null;
		}

		if (depthTexture != null) {
			depthTexture.close();
			depthTexture = null;
		}

		if (colorTexture != null) {
			colorTexture.close();
			colorTexture = null;
		}
	}
}
