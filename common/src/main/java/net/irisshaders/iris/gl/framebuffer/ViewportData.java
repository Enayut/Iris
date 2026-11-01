package net.irisshaders.iris.gl.framebuffer;

public record ViewportData(float scale, float viewportX, float viewportY) {
   private static final ViewportData DEFAULT = new ViewportData(1.0F, 0.0F, 0.0F);

   public static ViewportData defaultValue() {
      return DEFAULT;
   }
}
