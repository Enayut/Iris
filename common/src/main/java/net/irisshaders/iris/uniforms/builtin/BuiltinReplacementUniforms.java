package net.irisshaders.iris.uniforms.builtin;

import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import org.joml.Matrix4f;

public class BuiltinReplacementUniforms {
   private static final Matrix4f lightmapTextureMatrix = new Matrix4f(
      0.00390625F, 0.0F, 0.0F, 0.0F, 0.0F, 0.00390625F, 0.0F, 0.0F, 0.0F, 0.0F, 0.00390625F, 0.0F, 0.03125F, 0.03125F, 0.03125F, 1.0F
   );

   public static void addBuiltinReplacementUniforms(UniformHolder uniforms) {
      uniforms.uniformMatrix(UniformUpdateFrequency.ONCE, "iris_LightmapTextureMatrix", () -> lightmapTextureMatrix);
   }
}
