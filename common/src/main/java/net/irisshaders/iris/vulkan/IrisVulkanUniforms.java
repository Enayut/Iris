package net.irisshaders.iris.vulkan;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.state.StateUpdateNotifiers;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformType;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.FogUniforms;
import net.irisshaders.iris.uniforms.IrisInternalUniforms;
import net.irisshaders.iris.gl.state.FogMode;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;

/**
 * Collects Iris's uniform suppliers by name and GLSL type, and writes their current values into the std140 block
 * layout that {@link IrisVulkanGlslTranslator} produced for a program. The suppliers are the same ones Iris's
 * OpenGL path uses; only the transport differs (one UBO per program instead of glUniform calls).
 *
 * <p>Registration reuses {@link CommonUniforms#addNonDynamicUniforms} unchanged. Of the dynamic uniforms only the
 * OpenGL-free ones are registered: {@code atlasSize}, {@code gtextureId}, {@code gtextureSize} and
 * {@code blendFunc} read OpenGL state and are left unsupplied, so screen passes see 0 for them.</p>
 */
public final class IrisVulkanUniforms implements DynamicUniformHolder {
	private interface Writer {
		void write(ByteBuffer buffer, int offset);
	}

	private record Entry(String glslType, Writer writer) {
	}

	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private final Map<String, String> unsupported = new HashMap<>();
	private final java.util.Set<String> failing = new java.util.HashSet<>();

	public static IrisVulkanUniforms forScreenPasses(IdMap idMap, PackDirectives directives, FrameUpdateNotifier updateNotifier) {
		IrisVulkanUniforms uniforms = new IrisVulkanUniforms();
		CommonUniforms.addNonDynamicUniforms(uniforms, idMap, directives, updateNotifier);
		FogUniforms.addFogUniforms(uniforms, FogMode.OFF);
		IrisInternalUniforms.addFogUniforms(uniforms, FogMode.OFF);
		uniforms.uniform1i("entityId", CapturedRenderingState.INSTANCE::getCurrentRenderedEntity, StateUpdateNotifiers.fallbackEntityNotifier);
		uniforms.uniform1i("textureReloadCount", CapturedRenderingState.INSTANCE::getTextureReloadCount, StateUpdateNotifiers.bindTextureNotifier);
		// Screen passes run outside any world-rendering phase.
		uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "renderStage", WorldRenderingPhase.NONE::ordinal);
		return uniforms;
	}

	/** GLSL type of every supplied uniform, for {@link IrisVulkanGlslTranslator#translate}. */
	public Map<String, String> suppliedTypes() {
		Map<String, String> types = new HashMap<>();
		entries.forEach((name, entry) -> types.put(name, entry.glslType));
		return Collections.unmodifiableMap(types);
	}

	/** Names registered through methods that have no std140 mapping here (arrays); they stay unsupplied. */
	public Map<String, String> unsupported() {
		return Collections.unmodifiableMap(unsupported);
	}

	/** Writes the current value of every field in {@code fields} at its std140 offset. */
	public void write(List<IrisVulkanGlslTranslator.UniformField> fields, ByteBuffer buffer) {
		for (IrisVulkanGlslTranslator.UniformField field : fields) {
			Entry entry = entries.get(field.name());
			if (entry == null) {
				throw new IllegalStateException("No supplier for uniform " + field.name());
			}

			try {
				entry.writer.write(buffer, field.offset());
			} catch (RuntimeException e) {
				// The buffer is zero-filled, so a failing supplier reads as 0 (an unset uniform under OpenGL).
				if (failing.add(field.name())) {
					Iris.logger.error("[Iris/Vulkan] Uniform " + field.name() + " could not be evaluated on Vulkan; it reads as 0.", e);
				}
			}
		}
	}

	private IrisVulkanUniforms put(String name, String glslType, Writer writer) {
		if (entries.put(name, new Entry(glslType, writer)) != null) {
			Iris.logger.debug("[Iris/Vulkan] Uniform " + name + " registered twice; the later registration wins.");
		}

		return this;
	}

	private static Writer floats(FloatSupplier value) {
		return (buffer, offset) -> buffer.putFloat(offset, value.getAsFloat());
	}

	private static Writer vec2(Supplier<Vector2f> value) {
		return (buffer, offset) -> value.get().get(offset, buffer);
	}

	private static Writer vec3(Supplier<Vector3f> value) {
		return (buffer, offset) -> value.get().get(offset, buffer);
	}

	private static Writer vec4(Supplier<Vector4f> value) {
		return (buffer, offset) -> value.get().get(offset, buffer);
	}

	private static Writer ivec2(Supplier<Vector2i> value) {
		return (buffer, offset) -> value.get().get(offset, buffer);
	}

	private static Writer ivec3(Supplier<Vector3i> value) {
		return (buffer, offset) -> value.get().get(offset, buffer);
	}

	private static Writer ivec4(Supplier<Vector4i> value) {
		return (buffer, offset) -> value.get().get(offset, buffer);
	}

	private static Writer mat4(Supplier<Matrix4fc> value) {
		return (buffer, offset) -> value.get().get(offset, buffer);
	}

	private static Writer mat3(Supplier<Matrix3fc> value) {
		// std140 stores each mat3 column in a vec4 slot.
		return (buffer, offset) -> value.get().get3x4(offset, buffer);
	}

	private static Writer mat4FromArray(Supplier<float[]> value) {
		return (buffer, offset) -> {
			float[] values = value.get();
			for (int i = 0; i < 16; i++) {
				buffer.putFloat(offset + i * 4, values[i]);
			}
		};
	}

	@Override
	public UniformHolder uniform1f(UniformUpdateFrequency frequency, String name, FloatSupplier value) {
		return put(name, "float", floats(value));
	}

	@Override
	public UniformHolder uniform1f(UniformUpdateFrequency frequency, String name, IntSupplier value) {
		return put(name, "float", floats(() -> (float) value.getAsInt()));
	}

	@Override
	public UniformHolder uniform1f(UniformUpdateFrequency frequency, String name, DoubleSupplier value) {
		return put(name, "float", floats(() -> (float) value.getAsDouble()));
	}

	@Override
	public UniformHolder uniform1i(UniformUpdateFrequency frequency, String name, IntSupplier value) {
		return put(name, "int", (buffer, offset) -> buffer.putInt(offset, value.getAsInt()));
	}

	@Override
	public UniformHolder uniform1b(UniformUpdateFrequency frequency, String name, BooleanSupplier value) {
		// std140 bool is a 4-byte value.
		return put(name, "bool", (buffer, offset) -> buffer.putInt(offset, value.getAsBoolean() ? 1 : 0));
	}

	@Override
	public UniformHolder uniform2f(UniformUpdateFrequency frequency, String name, Supplier<Vector2f> value) {
		return put(name, "vec2", vec2(value));
	}

	@Override
	public UniformHolder uniform2i(UniformUpdateFrequency frequency, String name, Supplier<Vector2i> value) {
		return put(name, "ivec2", ivec2(value));
	}

	@Override
	public UniformHolder uniform3f(UniformUpdateFrequency frequency, String name, Supplier<Vector3f> value) {
		return put(name, "vec3", vec3(value));
	}

	@Override
	public UniformHolder uniform3i(UniformUpdateFrequency frequency, String name, Supplier<Vector3i> value) {
		return put(name, "ivec3", ivec3(value));
	}

	@Override
	public UniformHolder uniformTruncated3f(UniformUpdateFrequency frequency, String name, Supplier<Vector4f> value) {
		return put(name, "vec3", (buffer, offset) -> {
			Vector4f v = value.get();
			buffer.putFloat(offset, v.x).putFloat(offset + 4, v.y).putFloat(offset + 8, v.z);
		});
	}

	@Override
	public UniformHolder uniform3d(UniformUpdateFrequency frequency, String name, Supplier<Vector3d> value) {
		return put(name, "vec3", (buffer, offset) -> {
			Vector3d v = value.get();
			buffer.putFloat(offset, (float) v.x).putFloat(offset + 4, (float) v.y).putFloat(offset + 8, (float) v.z);
		});
	}

	@Override
	public UniformHolder uniform4f(UniformUpdateFrequency frequency, String name, Supplier<Vector4f> value) {
		return put(name, "vec4", vec4(value));
	}

	@Override
	public UniformHolder uniform4fArray(UniformUpdateFrequency frequency, String name, Supplier<float[]> value) {
		unsupported.put(name, "vec4[]");
		return this;
	}

	@Override
	public UniformHolder uniformMatrix(UniformUpdateFrequency frequency, String name, Supplier<Matrix4fc> value) {
		return put(name, "mat4", mat4(value));
	}

	@Override
	public UniformHolder uniformMatrix3(UniformUpdateFrequency frequency, String name, Supplier<Matrix3fc> value) {
		return put(name, "mat3", mat3(value));
	}

	@Override
	public UniformHolder uniformMatrixFromArray(UniformUpdateFrequency frequency, String name, Supplier<float[]> value) {
		return put(name, "mat4", mat4FromArray(value));
	}

	@Override
	public UniformHolder externallyManagedUniform(String name, UniformType type) {
		// Set by vanilla's own uniform blocks under OpenGL; screen passes do not have them.
		return this;
	}

	@Override
	public DynamicUniformHolder uniform1f(String name, FloatSupplier value, ValueUpdateNotifier notifier) {
		put(name, "float", floats(value));
		return this;
	}

	@Override
	public DynamicUniformHolder uniform1f(String name, IntSupplier value, ValueUpdateNotifier notifier) {
		put(name, "float", floats(() -> (float) value.getAsInt()));
		return this;
	}

	@Override
	public DynamicUniformHolder uniform1f(String name, DoubleSupplier value, ValueUpdateNotifier notifier) {
		put(name, "float", floats(() -> (float) value.getAsDouble()));
		return this;
	}

	@Override
	public DynamicUniformHolder uniform1i(String name, IntSupplier value, ValueUpdateNotifier notifier) {
		put(name, "int", (buffer, offset) -> buffer.putInt(offset, value.getAsInt()));
		return this;
	}

	@Override
	public DynamicUniformHolder uniform2f(String name, Supplier<Vector2f> value, ValueUpdateNotifier notifier) {
		put(name, "vec2", vec2(value));
		return this;
	}

	@Override
	public DynamicUniformHolder uniform2i(String name, Supplier<Vector2i> value, ValueUpdateNotifier notifier) {
		put(name, "ivec2", ivec2(value));
		return this;
	}

	@Override
	public DynamicUniformHolder uniform3f(String name, Supplier<Vector3f> value, ValueUpdateNotifier notifier) {
		put(name, "vec3", vec3(value));
		return this;
	}

	@Override
	public DynamicUniformHolder uniform4f(String name, Supplier<Vector4f> value, ValueUpdateNotifier notifier) {
		put(name, "vec4", vec4(value));
		return this;
	}

	@Override
	public DynamicUniformHolder uniform4fArray(String name, Supplier<float[]> value, ValueUpdateNotifier notifier) {
		unsupported.put(name, "vec4[]");
		return this;
	}

	@Override
	public DynamicUniformHolder uniform4i(String name, Supplier<Vector4i> value, ValueUpdateNotifier notifier) {
		put(name, "ivec4", ivec4(value));
		return this;
	}

	@Override
	public DynamicUniformHolder uniformMatrix(String name, Supplier<Matrix4fc> value, ValueUpdateNotifier notifier) {
		put(name, "mat4", mat4(value));
		return this;
	}

	@Override
	public DynamicUniformHolder uniformMatrix3(String name, Supplier<Matrix3fc> value, ValueUpdateNotifier notifier) {
		put(name, "mat3", mat3(value));
		return this;
	}
}
