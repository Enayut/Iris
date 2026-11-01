package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.DeviceType;
import com.mojang.renderpearl.api.device.GpuDevice;
import net.irisshaders.iris.Iris;

/**
 * Phase 1 foundation: detects whether Minecraft 26.3 is running on its native
 * Vulkan renderer (renderpearl {@code VulkanBackend}) and exposes the active
 * {@link GpuDevice} to the Iris Vulkan backend.
 *
 * <p>Minecraft 26.3 selects its backend in {@code PreferredGraphicsApi#getBackendsToTry()}
 * before device creation; by the time {@link RenderSystem#getDevice()} returns a
 * device, the backend is fixed for the process lifetime. We therefore only need
 * to query {@link DeviceInfo#backendName()} here.</p>
 */
public final class IrisVulkanBackend {
	private static boolean detected;
	private static boolean vulkanActive;
	private static DeviceInfo deviceInfo;
	private static boolean rtx3050;

	/**
	 * Opt-in flag for the experimental Vulkan screen-pass chain. Note that the chain
	 * is driven from {@code IrisRenderingPipeline}, which is never constructed while
	 * Vulkan is active (see {@link IrisVulkanCapabilities}), so this flag currently
	 * has no effect on a Vulkan device.
	 */
	public static boolean isChainRequested() {
		return Boolean.getBoolean("iris.vulkan");
	}

	public static void detect() {
		if (detected) {
			return;
		}

		detected = true;
		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			Iris.logger.info("[Iris/Vulkan] No renderpearl GpuDevice present yet; deferring Vulkan detection.");
			detected = false;
			return;
		}

		deviceInfo = device.getDeviceInfo();
		vulkanActive = "Vulkan".equalsIgnoreCase(deviceInfo.backendName());

		if (vulkanActive) {
			String name = deviceInfo.name();
			rtx3050 = name != null && name.contains("3050");
			Iris.logger.info("[Iris/Vulkan] Native Vulkan backend detected: {} (vendor: {}, type: {})", name, deviceInfo.vendorName(), deviceInfo.type());
			Iris.logger.info("[Iris/Vulkan] Driver: {}", deviceInfo.driverInfo());
			if (rtx3050) {
				Iris.logger.info("[Iris/Vulkan] NVIDIA RTX 3050 selected as the active Vulkan device.");
			} else {
				Iris.logger.info("[Iris/Vulkan] Discrete GPU selected: {} ({})", name, deviceInfo.type() == DeviceType.DISCRETE ? "discrete" : deviceInfo.type());
			}

			if (isChainRequested()) {
				Iris.logger.warn("[Iris/Vulkan] -Diris.vulkan=true set: the experimental screen-pass chain is incomplete and does not render shader packs.");
			}
		} else {
			Iris.logger.info("[Iris/Vulkan] Active backend is {} (not Vulkan); Vulkan path idle.", deviceInfo.backendName());
		}
	}

	public static boolean isVulkanDevice() {
		return vulkanActive;
	}

	/** True when the Vulkan screen-pass chain should execute (Vulkan device + opt-in). */
	public static boolean isChainActive() {
		return vulkanActive && isChainRequested();
	}

	public static GpuDevice device() {
		return RenderSystem.getDevice();
	}

	public static DeviceInfo deviceInfo() {
		return deviceInfo;
	}

	public static boolean isRtx3050() {
		return rtx3050;
	}

	private IrisVulkanBackend() {
	}
}
