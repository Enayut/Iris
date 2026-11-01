package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

/**
 * A shader pack colour target ({@code colortexN}) on the Vulkan device: a main/alt texture pair, mirroring Iris's
 * OpenGL ping-pong scheme. Which of the two a pass reads or writes is decided by the pass's flip snapshot.
 */
final class IrisVulkanTarget implements AutoCloseable {
	private static final int USAGE = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING
		| GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST;

	final int index;
	final GpuFormat format;
	private int width;
	private int height;
	private GpuTexture main;
	private GpuTexture alt;
	private GpuTextureView mainView;
	private GpuTextureView altView;

	IrisVulkanTarget(int index, GpuFormat format, int width, int height) {
		this.index = index;
		this.format = format;
		allocate(width, height);
	}

	private void allocate(int width, int height) {
		this.width = width;
		this.height = height;
		main = RenderSystem.getDevice().createTexture(() -> "iris colortex" + index + " main", USAGE, format, width, height, 1, 1);
		alt = RenderSystem.getDevice().createTexture(() -> "iris colortex" + index + " alt", USAGE, format, width, height, 1, 1);
		mainView = RenderSystem.getDevice().createTextureView(main);
		altView = RenderSystem.getDevice().createTextureView(alt);
	}

	void resize(int width, int height) {
		if (width != this.width || height != this.height) {
			close();
			allocate(width, height);
		}
	}

	GpuTexture texture(boolean alternate) {
		return alternate ? alt : main;
	}

	GpuTextureView view(boolean alternate) {
		return alternate ? altView : mainView;
	}

	GpuSampler sampler() {
		return VulkanSamplers.clamp(!IrisVulkanFormats.isInteger(format));
	}

	@Override
	public void close() {
		mainView.close();
		altView.close();
		main.close();
		alt.close();
	}
}
