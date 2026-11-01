package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import java.util.OptionalDouble;

/**
 * The handful of sampler states Iris screen passes need, created once on the RenderPearl device. They match Iris's
 * OpenGL samplers: render targets and depth use clamp-to-edge, the noise texture repeats, custom textures follow
 * their mcmeta, and none use anisotropy.
 */
final class VulkanSamplers {
	private static GpuSampler linearClamp;
	private static GpuSampler nearestClamp;
	private static GpuSampler linearRepeat;
	private static GpuSampler nearestRepeat;

	private VulkanSamplers() {
	}

	static GpuSampler clamp(boolean linear) {
		if (linear) {
			if (linearClamp == null) {
				linearClamp = create(AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR);
			}
			return linearClamp;
		}

		if (nearestClamp == null) {
			nearestClamp = create(AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST);
		}
		return nearestClamp;
	}

	static GpuSampler linearRepeat() {
		return repeat(true);
	}

	static GpuSampler repeat(boolean linear) {
		if (linear) {
			if (linearRepeat == null) {
				linearRepeat = create(AddressMode.REPEAT, FilterMode.LINEAR);
			}
			return linearRepeat;
		}

		if (nearestRepeat == null) {
			nearestRepeat = create(AddressMode.REPEAT, FilterMode.NEAREST);
		}
		return nearestRepeat;
	}

	private static GpuSampler create(AddressMode address, FilterMode filter) {
		return RenderSystem.getDevice().createSampler(address, address, filter, filter, 1, OptionalDouble.empty());
	}
}
