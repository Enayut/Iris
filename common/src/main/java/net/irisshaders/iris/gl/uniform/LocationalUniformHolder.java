package net.irisshaders.iris.gl.uniform;

import java.util.OptionalInt;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;

public interface LocationalUniformHolder extends UniformHolder {
   LocationalUniformHolder addUniform(UniformUpdateFrequency var1, Uniform var2);

   OptionalInt location(String var1, UniformType var2);

   default LocationalUniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, FloatSupplier value) {
      this.location(name, UniformType.FLOAT).ifPresent(id -> this.addUniform(updateFrequency, new FloatUniform(id, value)));
      return this;
   }

   default LocationalUniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, IntSupplier value) {
      this.location(name, UniformType.FLOAT).ifPresent(id -> this.addUniform(updateFrequency, new FloatUniform(id, () -> value.getAsInt())));
      return this;
   }

   default LocationalUniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, DoubleSupplier value) {
      this.location(name, UniformType.FLOAT).ifPresent(id -> this.addUniform(updateFrequency, new FloatUniform(id, () -> (float)value.getAsDouble())));
      return this;
   }

   default LocationalUniformHolder uniform1i(UniformUpdateFrequency updateFrequency, String name, IntSupplier value) {
      this.location(name, UniformType.INT).ifPresent(id -> this.addUniform(updateFrequency, new IntUniform(id, value)));
      return this;
   }

   default LocationalUniformHolder uniform1b(UniformUpdateFrequency updateFrequency, String name, BooleanSupplier value) {
      this.location(name, UniformType.INT).ifPresent(id -> this.addUniform(updateFrequency, new BooleanUniform(id, value)));
      return this;
   }

   default LocationalUniformHolder uniform2f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2f> value) {
      this.location(name, UniformType.VEC2).ifPresent(id -> this.addUniform(updateFrequency, new Vector2Uniform(id, value)));
      return this;
   }

   default LocationalUniformHolder uniform2i(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2i> value) {
      this.location(name, UniformType.VEC2I).ifPresent(id -> this.addUniform(updateFrequency, new Vector2IntegerJomlUniform(id, value)));
      return this;
   }

   default LocationalUniformHolder uniform3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3f> value) {
      this.location(name, UniformType.VEC3).ifPresent(id -> this.addUniform(updateFrequency, new Vector3Uniform(id, value)));
      return this;
   }

   default LocationalUniformHolder uniform3i(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3i> value) {
      this.location(name, UniformType.VEC3I).ifPresent(id -> this.addUniform(updateFrequency, new Vector3IntegerUniform(id, value)));
      return this;
   }

   default LocationalUniformHolder uniformTruncated3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4f> value) {
      this.location(name, UniformType.VEC3).ifPresent(id -> this.addUniform(updateFrequency, Vector3Uniform.truncated(id, value)));
      return this;
   }

   default LocationalUniformHolder uniform3d(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3d> value) {
      this.location(name, UniformType.VEC3).ifPresent(id -> this.addUniform(updateFrequency, Vector3Uniform.converted(id, value)));
      return this;
   }

   default LocationalUniformHolder uniform4f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4f> value) {
      this.location(name, UniformType.VEC4).ifPresent(id -> this.addUniform(updateFrequency, new Vector4Uniform(id, value)));
      return this;
   }

   default LocationalUniformHolder uniform4fArray(UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value) {
      this.location(name, UniformType.VEC4).ifPresent(id -> this.addUniform(updateFrequency, new Vector4ArrayUniform(id, value)));
      return this;
   }

   default LocationalUniformHolder uniformMatrix(UniformUpdateFrequency updateFrequency, String name, Supplier<Matrix4fc> value) {
      this.location(name, UniformType.MAT4).ifPresent(id -> this.addUniform(updateFrequency, new MatrixUniform(id, value)));
      return this;
   }

   default LocationalUniformHolder uniformMatrix3(UniformUpdateFrequency updateFrequency, String name, Supplier<Matrix3fc> value) {
      this.location(name, UniformType.MAT3).ifPresent(id -> this.addUniform(updateFrequency, new Matrix3Uniform(id, value)));
      return this;
   }

   default LocationalUniformHolder uniformMatrixFromArray(UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value) {
      this.location(name, UniformType.MAT4).ifPresent(id -> this.addUniform(updateFrequency, new MatrixFromFloatArrayUniform(id, value)));
      return this;
   }
}
