package net.irisshaders.iris.vulkan;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * Phase 2: bridges Iris's transformed shaderpack GLSL into renderpearl's
 * compilation pipeline. The device's {@code PipelineCache} resolves shader
 * includes through this {@link ShaderSource}; every lookup returns the GLSL
 * that Iris's {@code TransformPatcher} produced for the pack program, which
 * the {@code VulkanBackend} then compiles to SPIR-V (via its embedded
 * glslang frontend) and builds into a {@code VkShaderModule} + pipeline.
 */
public final class IrisVulkanShaderSourceMap implements ShaderSource {
	private final Map<SourceKey, String> sources;

	public IrisVulkanShaderSourceMap(Map<SourceKey, String> sources) {
		this.sources = sources;
	}

	@Override
	public String getShader(Identifier id, ShaderType type) {
		String source = sources.get(new SourceKey(id, type));
		if (source != null) {
			return source;
		}

		throw new IllegalStateException("Iris/Vulkan: no compiled GLSL registered for " + id + " (" + type + ")");
	}

	@Override
	public CachedIncludeSource getInclude(Identifier id) {
		return null;
	}

	public void registerAll(Map<SourceKey, String> additional) {
		sources.putAll(additional);
	}

	@Override
	public void close() {
		sources.clear();
	}

	public record SourceKey(Identifier id, ShaderType type) {
	}
}
