package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;

/**
 * Answers "is the GPU device Minecraft created a RenderPearl Vulkan device?". This is the actual backend, not the
 * {@code preferredGraphicsBackend} option (RenderPearl falls back to the other API if the preferred one fails).
 */
public final class IrisVulkanBackend {
	/**
	 * Opt-in for the experimental Vulkan screen-pass renderer ({@code -Diris.vulkan.experimental=true}). Without it,
	 * Iris on Vulkan only runs diagnostics and never touches rendering.
	 */
	public static final boolean EXPERIMENTAL_RENDERING = Boolean.getBoolean("iris.vulkan.experimental");

	private IrisVulkanBackend() {
	}

	public static boolean isActive() {
		GpuDevice device = RenderSystem.tryGetDevice();
		return device != null && "Vulkan".equalsIgnoreCase(device.getDeviceInfo().backendName());
	}
}
