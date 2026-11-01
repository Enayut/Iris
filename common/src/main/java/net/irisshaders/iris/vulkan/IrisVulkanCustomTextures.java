package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.texture.CustomTextureData;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

/**
 * The pack's PNG textures on Vulkan: {@code texture.<stage>.<sampler>}, {@code customTexture.<sampler>} and
 * {@code texture.noise}, uploaded once per pack with the filtering of NativeImageBackedCustomTexture (nearest and
 * repeating unless the texture's mcmeta asks for blur or clamp). Raw, resource and lightmap textures are not
 * supported yet; their samplers keep the screen passes' fallback binding.
 */
final class IrisVulkanCustomTextures implements AutoCloseable {
	record Texture(GpuTexture texture, GpuTextureView view, GpuSampler sampler) {
	}

	private final List<Texture> all = new ArrayList<>();
	private final Map<TextureStage, Map<String, Texture>> byStage = new EnumMap<>(TextureStage.class);
	private final Map<String, Texture> everyStage = new HashMap<>();
	private final List<String> unsupported = new ArrayList<>();
	private Texture noise;

	IrisVulkanCustomTextures(ShaderPack pack) {
		try {
			pack.getCustomTextureDataMap().forEach((stage, textures) -> textures.forEach((sampler, data) -> {
				Texture texture = load(stage + "." + sampler, data);
				if (texture != null) {
					byStage.computeIfAbsent(stage, s -> new HashMap<>()).put(sampler, texture);
				}
			}));
			pack.getIrisCustomTextureDataMap().forEach((sampler, data) -> {
				Texture texture = load(sampler, data);
				if (texture != null) {
					everyStage.put(sampler, texture);
				}
			});
			if (pack.getCustomNoiseTexture() != null) {
				noise = load("noise", pack.getCustomNoiseTexture());
			}
		} catch (RuntimeException e) {
			close();
			throw e;
		}

		if (!all.isEmpty() || !unsupported.isEmpty()) {
			Iris.logger.info("[Iris/Vulkan] Loaded {} custom textures (stages {}, every stage {}, noise {}){}.", all.size(), byStage.keySet(), everyStage.keySet(),
				noise != null, unsupported.isEmpty() ? "" : "; not supported on Vulkan yet: " + unsupported);
		}
	}

	/** The texture a {@code texture.<stage>.<sampler>} directive binds, which takes priority over render targets. */
	Texture forStage(TextureStage stage, String sampler) {
		Map<String, Texture> textures = byStage.get(stage);
		return textures != null ? textures.get(sampler) : null;
	}

	/** A {@code customTexture.<sampler>} texture, bound in every program that declares the sampler. */
	Texture forEveryStage(String sampler) {
		return everyStage.get(sampler);
	}

	/** The pack's {@code texture.noise}, or null for Iris's generated noise. */
	Texture noise() {
		return noise;
	}

	private Texture load(String name, CustomTextureData data) {
		if (!(data instanceof CustomTextureData.PngData png)) {
			unsupported.add(name + " (" + (data == null ? "unreadable" : data.getClass().getSimpleName()) + ")");
			return null;
		}

		GpuTexture texture;
		try (NativeImage image = NativeImage.read(png.getContent())) {
			texture = RenderSystem.getDevice().createTexture(() -> "iris vulkan custom texture " + name, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
				GpuFormat.RGBA8_UNORM, image.getWidth(), image.getHeight(), 1, 1);
			try {
				RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
			} catch (RuntimeException e) {
				texture.close();
				throw e;
			}
		} catch (IOException e) {
			Iris.logger.error("[Iris/Vulkan] Could not decode custom texture " + name, e);
			unsupported.add(name + " (undecodable PNG)");
			return null;
		}

		boolean blur = png.getFilteringData().shouldBlur();
		GpuSampler sampler = png.getFilteringData().shouldClamp() ? VulkanSamplers.clamp(blur) : VulkanSamplers.repeat(blur);
		Texture loaded = new Texture(texture, RenderSystem.getDevice().createTextureView(texture), sampler);
		all.add(loaded);
		return loaded;
	}

	@Override
	public void close() {
		for (Texture texture : all) {
			texture.view.close();
			texture.texture.close();
		}
		all.clear();
		byStage.clear();
		everyStage.clear();
		noise = null;
	}
}
