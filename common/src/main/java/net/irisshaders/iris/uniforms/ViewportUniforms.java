package net.irisshaders.iris.uniforms;

import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.minecraft.client.Minecraft;

public final class ViewportUniforms {
   private ViewportUniforms() {
   }

   public static void addViewportUniforms(UniformHolder uniforms) {
      uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "viewHeight", () -> Minecraft.getInstance().gameRenderer.mainRenderTarget().height)
         .uniform1f(UniformUpdateFrequency.PER_FRAME, "viewWidth", () -> Minecraft.getInstance().gameRenderer.mainRenderTarget().width)
         .uniform1f(UniformUpdateFrequency.PER_FRAME, "aspectRatio", ViewportUniforms::getAspectRatio);
   }

   private static float getAspectRatio() {
      return (float)Minecraft.getInstance().gameRenderer.mainRenderTarget().width / Minecraft.getInstance().gameRenderer.mainRenderTarget().height;
   }
}
