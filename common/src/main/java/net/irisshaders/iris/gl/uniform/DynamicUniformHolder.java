package net.irisshaders.iris.gl.uniform;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4i;

public interface DynamicUniformHolder extends UniformHolder {
   DynamicUniformHolder uniform1f(String var1, FloatSupplier var2, ValueUpdateNotifier var3);

   DynamicUniformHolder uniform1f(String var1, IntSupplier var2, ValueUpdateNotifier var3);

   DynamicUniformHolder uniform1f(String var1, DoubleSupplier var2, ValueUpdateNotifier var3);

   DynamicUniformHolder uniform1i(String var1, IntSupplier var2, ValueUpdateNotifier var3);

   DynamicUniformHolder uniform2f(String var1, Supplier<Vector2f> var2, ValueUpdateNotifier var3);

   DynamicUniformHolder uniform2i(String var1, Supplier<Vector2i> var2, ValueUpdateNotifier var3);

   DynamicUniformHolder uniform3f(String var1, Supplier<Vector3f> var2, ValueUpdateNotifier var3);

   DynamicUniformHolder uniform4f(String var1, Supplier<Vector4f> var2, ValueUpdateNotifier var3);

   DynamicUniformHolder uniform4fArray(String var1, Supplier<float[]> var2, ValueUpdateNotifier var3);

   DynamicUniformHolder uniform4i(String var1, Supplier<Vector4i> var2, ValueUpdateNotifier var3);

   DynamicUniformHolder uniformMatrix(String var1, Supplier<Matrix4fc> var2, ValueUpdateNotifier var3);

   DynamicUniformHolder uniformMatrix3(String var1, Supplier<Matrix3fc> var2, ValueUpdateNotifier var3);
}
