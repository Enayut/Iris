package net.irisshaders.iris.gl.blending;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.opengl.GlStateManager.DepthState;
import java.util.Arrays;
import net.irisshaders.iris.mixin.GlStateManagerAccessor;

public class DepthColorStorage {
   private static boolean originalDepthEnable;
   private static final int[] originalColor = new int[GlStateManagerAccessor.getCOLOR_MASK().length];
   private static boolean depthColorLocked;

   public static boolean isDepthColorLocked() {
      return depthColorLocked;
   }

   public static void disableDepthColor() {
      if (!depthColorLocked) {
         DepthState depthState = GlStateManagerAccessor.getDEPTH();
         originalDepthEnable = depthState.mask;

         for (int i = 0; i < originalColor.length; i++) {
            originalColor[i] = GlStateManagerAccessor.getCOLOR_MASK()[i];
         }
      }

      depthColorLocked = false;
      GlStateManager._depthMask(false);
      GlStateManager._colorMask(0);
      depthColorLocked = true;
   }

   public static void deferDepthEnable(boolean enabled) {
      originalDepthEnable = enabled;
   }

   public static void deferColorMask(int writeMask) {
      Arrays.fill(originalColor, writeMask);
   }

   public static void deferColorMask(int index, int writeMask) {
      originalColor[index] = writeMask;
   }

   public static void unlockDepthColor() {
      if (depthColorLocked) {
         depthColorLocked = false;
         GlStateManager._depthMask(originalDepthEnable);

         for (int i = 0; i < originalColor.length; i++) {
            GlStateManager._colorMask(i, originalColor[i]);
         }
      }
   }
}
