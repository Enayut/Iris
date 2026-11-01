package net.irisshaders.iris.gl.texture;

import com.mojang.renderpearl.backend.opengl.GlStateManager;

public class TextureUploadHelper {
   private TextureUploadHelper() {
   }

   public static void resetTextureUploadState() {
      GlStateManager._pixelStore(3314, 0);
      GlStateManager._pixelStore(3315, 0);
      GlStateManager._pixelStore(3316, 0);
      GlStateManager._pixelStore(32878, 0);
      GlStateManager._pixelStore(3317, 4);
   }
}
