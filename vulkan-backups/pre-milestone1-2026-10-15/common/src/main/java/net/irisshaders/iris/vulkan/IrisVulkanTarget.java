package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;

/**
 * Phase 3 resource model: a Vulkan-native Iris colortex. Each Iris colortex is
 * a pair of {@link GpuTexture}s (main/alt, mirroring the OpenGL ping-pong
 * scheme) plus cached {@link GpuTextureView}s and {@link GpuSampler}s for
 * descriptor binding.
 *
 * <p>Textures are allocated through the renderpearl {@code GpuDevice} so they
 * live in the same Vulkan memory/allocator as the rest of the game and get
 * real {@code VkImage}/{@code VkImageView} handles from the
 * {@code VulkanBackend}.</p>
 */
public final class IrisVulkanTarget {
	private final int index;
	private final String name;
	private final GpuFormat format;
	private final boolean allowsLinear;
	private GpuTexture mainTexture;
	private GpuTexture altTexture;
	private GpuTextureView mainView;
	private GpuTextureView altView;
	private GpuSampler mainSampler;
	private GpuSampler altSampler;
	private int width;
	private int height;
	private boolean mipmapsOnAlt;
	private boolean mipmapsOnMain;
	private boolean valid = true;

	private IrisVulkanTarget(int index, String name, GpuFormat format, boolean allowsLinear, int width, int height) {
		this.index = index;
		this.name = name;
		this.format = format;
		this.allowsLinear = allowsLinear;
		this.width = width;
		this.height = height;
		allocate();
	}

	public static IrisVulkanTarget create(int index, String name, InternalTextureFormat irisFormat, int width, int height) {
		GpuFormat format = IrisVulkanFormats.toGpuFormat(irisFormat);
		boolean integerFormat = irisFormat.getPixelFormat().isInteger();
		return new IrisVulkanTarget(index, name, format, !integerFormat, width, height);
	}

	private void allocate() {
		int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST;
		mainTexture = RenderSystem.getDevice().createTexture(() -> "iris colortex" + index + " main", usage, format, width, height, 1, 1);
		altTexture = RenderSystem.getDevice().createTexture(() -> "iris colortex" + index + " alt", usage, format, width, height, 1, 1);
		mainView = RenderSystem.getDevice().createTextureView(mainTexture);
		altView = RenderSystem.getDevice().createTextureView(altTexture);
	}

	public void resize(int newWidth, int newHeight) {
		if (newWidth == width && newHeight == height) {
			return;
		}

		width = newWidth;
		height = newHeight;
		release();
		allocate();
	}

	public void swap() {
		GpuTexture t = mainTexture;
		GpuTextureView v = mainView;
		mainTexture = altTexture;
		mainView = altView;
		altTexture = t;
		altView = v;
	}

	public int index() {
		return index;
	}

	public String name() {
		return name;
	}

	public GpuFormat format() {
		return format;
	}

	public GpuTexture mainTexture() {
		return mainTexture;
	}

	public GpuTexture altTexture() {
		return altTexture;
	}

	public GpuTextureView mainView() {
		return mainView;
	}

	public GpuTextureView altView() {
		return altView;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public boolean allowsLinear() {
		return allowsLinear;
	}

	public void setMipmapsOnMain(boolean value) {
		mipmapsOnMain = value;
	}

	public void setMipmapsOnAlt(boolean value) {
		mipmapsOnAlt = value;
	}

	/** Sampler for the currently "front" (main) image of this colortex. */
	public GpuSampler mainSampler() {
		return VulkanSamplers.get(allowsLinear && !mipmapsOnMain, mipmapsOnMain);
	}

	/** Sampler for the currently "back" (alt) image of this colortex. */
	public GpuSampler altSampler() {
		return VulkanSamplers.get(allowsLinear && !mipmapsOnAlt, mipmapsOnAlt);
	}

	public void destroy() {
		if (!valid) {
			return;
		}

		valid = false;
		release();
	}

	private void release() {
		if (mainView != null) {
			mainView.close();
			mainView = null;
		}

		if (altView != null) {
			altView.close();
			altView = null;
		}

		if (mainTexture != null) {
			mainTexture.close();
			mainTexture = null;
		}

		if (altTexture != null) {
			altTexture.close();
			altTexture = null;
		}

		mainSampler = null;
		altSampler = null;
	}
}
