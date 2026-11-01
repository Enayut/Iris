package net.irisshaders.iris.uniforms;

import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.minecraft.client.Minecraft;
import org.joml.Vector2f;

public class VanillaUniforms {
   public static void addVanillaUniforms(DynamicUniformHolder uniforms) {
      Vector2f cachedScreenSize = new Vector2f();
      uniforms.uniform2f(
         "iris_ScreenSize",
         () -> cachedScreenSize.set(
            Minecraft.getInstance().gameRenderer.mainRenderTarget().width, Minecraft.getInstance().gameRenderer.mainRenderTarget().height
         ),
         listener -> {}
      );
   }
}
