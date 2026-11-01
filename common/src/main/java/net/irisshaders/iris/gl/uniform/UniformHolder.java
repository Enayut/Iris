package net.irisshaders.iris.gl.uniform;

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

public interface UniformHolder {
   UniformHolder uniform1f(UniformUpdateFrequency var1, String var2, FloatSupplier var3);

   UniformHolder uniform1f(UniformUpdateFrequency var1, String var2, IntSupplier var3);

   UniformHolder uniform1f(UniformUpdateFrequency var1, String var2, DoubleSupplier var3);

   UniformHolder uniform1i(UniformUpdateFrequency var1, String var2, IntSupplier var3);

   UniformHolder uniform1b(UniformUpdateFrequency var1, String var2, BooleanSupplier var3);

   UniformHolder uniform2f(UniformUpdateFrequency var1, String var2, Supplier<Vector2f> var3);

   UniformHolder uniform2i(UniformUpdateFrequency var1, String var2, Supplier<Vector2i> var3);

   UniformHolder uniform3f(UniformUpdateFrequency var1, String var2, Supplier<Vector3f> var3);

   UniformHolder uniform3i(UniformUpdateFrequency var1, String var2, Supplier<Vector3i> var3);

   UniformHolder uniformTruncated3f(UniformUpdateFrequency var1, String var2, Supplier<Vector4f> var3);

   UniformHolder uniform3d(UniformUpdateFrequency var1, String var2, Supplier<Vector3d> var3);

   UniformHolder uniform4f(UniformUpdateFrequency var1, String var2, Supplier<Vector4f> var3);

   UniformHolder uniform4fArray(UniformUpdateFrequency var1, String var2, Supplier<float[]> var3);

   UniformHolder uniformMatrix(UniformUpdateFrequency var1, String var2, Supplier<Matrix4fc> var3);

   UniformHolder uniformMatrix3(UniformUpdateFrequency var1, String var2, Supplier<Matrix3fc> var3);

   UniformHolder uniformMatrixFromArray(UniformUpdateFrequency var1, String var2, Supplier<float[]> var3);

   UniformHolder externallyManagedUniform(String var1, UniformType var2);
}
