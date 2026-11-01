package net.irisshaders.iris.pbr.util;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.irisshaders.iris.gl.IrisRenderSystem;

public class TextureManipulationUtil {
   private static int colorFillFBO = -1;

   public static void fillWithColor(int textureId, int maxLevel, int rgba) {
      if (colorFillFBO == -1) {
         colorFillFBO = GlStateManager.glGenFramebuffers();
      }

      int previousFramebufferId = GlStateManager._getInteger(36006);
      float[] previousClearColor = new float[4];
      IrisRenderSystem.getFloatv(3106, previousClearColor);
      int previousTextureId = GlStateManager._getInteger(32873);
      int[] previousViewport = new int[4];
      IrisRenderSystem.getIntegerv(2978, previousViewport);
      GlStateManager._glBindFramebuffer(36160, colorFillFBO);
      IrisRenderSystem.clearColor((rgba >> 24 & 0xFF) / 255.0F, (rgba >> 16 & 0xFF) / 255.0F, (rgba >> 8 & 0xFF) / 255.0F, (rgba & 0xFF) / 255.0F);
      GlStateManager._bindTexture(textureId);

      for (int level = 0; level <= maxLevel; level++) {
         int width = GlStateManager._getTexLevelParameter(3553, level, 4096);
         int height = GlStateManager._getTexLevelParameter(3553, level, 4097);
         GlStateManager._viewport(0, 0, width, height);
         GlStateManager._glFramebufferTexture2D(36160, 36064, 3553, textureId, level);
         GlStateManager._clear(16384);
         GlStateManager._glFramebufferTexture2D(36160, 36064, 3553, 0, level);
      }

      GlStateManager._glBindFramebuffer(36160, previousFramebufferId);
      IrisRenderSystem.clearColor(previousClearColor[0], previousClearColor[1], previousClearColor[2], previousClearColor[3]);
      GlStateManager._bindTexture(previousTextureId);
      GlStateManager._viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
   }
}
