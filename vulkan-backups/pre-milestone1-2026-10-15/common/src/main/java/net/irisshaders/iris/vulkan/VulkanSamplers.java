package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared Vulkan sampler cache for the Iris Vulkan backend. Iris shaders only
 * need a handful of sampling states (linear/nearest, optionally mipped, with
 * CLAMP_TO_EDGE addressing), so samplers are created once via the renderpearl
 * device and reused for descriptor binding.
 */
final class VulkanSamplers {
	private static final ConcurrentHashMap<Long, GpuSampler> CACHE = new ConcurrentHashMap<>();

	private VulkanSamplers() {
	}

	static GpuSampler get(boolean linear, boolean mipped) {
		long key = (linear ? 1L : 0L) | (mipped ? 2L : 0L);
		return CACHE.computeIfAbsent(key, k -> RenderSystem.getDevice().createSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE,
			(linear || mipped) ? FilterMode.LINEAR : FilterMode.NEAREST,
			(linear || mipped) ? FilterMode.LINEAR : FilterMode.NEAREST,
			4,
			mipped ? java.util.OptionalDouble.of(1000.0) : java.util.OptionalDouble.of(0.25)
		));
	}

	static GpuSampler linearClamp() {
		return get(true, false);
	}

	static GpuSampler nearestClamp() {
		return get(false, false);
	}
}
