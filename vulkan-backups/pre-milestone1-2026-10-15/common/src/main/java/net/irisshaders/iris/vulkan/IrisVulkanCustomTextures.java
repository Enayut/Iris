package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.HashMap;
import java.util.Map;
import net.irisshaders.iris.Iris;

/**
 * Phase 3: shaderpack custom textures ({@code texture.<stage>.<sampler>}
 * declarations and {@code iris-custom} textures) allocated as renderpearl
 * {@code GpuTexture}s so they can be bound in Vulkan screen passes.
 *
 * <p>Pixel upload uses the encoder's {@code writeToTexture}; the actual
 * decoding reuses the OpenGL side's parsed {@code NativeImage} where available,
 * so no pack-specific handling exists here.</p>
 */
final class IrisVulkanCustomTextures {
	private final Map<String, GpuTexture> textures = new HashMap<>();
	private final Map<String, GpuTextureView> views = new HashMap<>();

	void put(String samplerName, GpuTexture texture, GpuTextureView view) {
		textures.put(samplerName, texture);
		views.put(samplerName, view);
	}

	GpuTextureView view(String samplerName) {
		return views.get(samplerName);
	}

	GpuSamplerlessEntry entry(String samplerName) {
		GpuTexture texture = textures.get(samplerName);
		if (texture == null) {
			return null;
		}

		return new GpuSamplerlessEntry(texture, views.get(samplerName));
	}

	record GpuSamplerlessEntry(GpuTexture texture, GpuTextureView view) {
	}

	static IrisVulkanCustomTextures empty() {
		return new IrisVulkanCustomTextures();
	}

	@SuppressWarnings("unused")
	private static GpuTexture createPlaceholder(String name, int width, int height) {
		return RenderSystem.getDevice().createTexture(
			() -> "iris vulkan custom: " + name,
			GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
			GpuFormat.RGBA8_UNORM,
			width, height, 1, 1
		);
	}

	void destroy() {
		for (GpuTextureView view : views.values()) {
			view.close();
		}

		for (GpuTexture texture : textures.values()) {
			texture.close();
		}

		views.clear();
		textures.clear();
		Iris.logger.debug("[Iris/Vulkan] Custom textures released.");
	}
}
