package net.irisshaders.iris.mixin;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.opengl.GlStateManager.BlendState;
import com.mojang.renderpearl.backend.opengl.GlStateManager.DepthState;
import com.mojang.renderpearl.backend.opengl.GlStateManager.TextureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GlStateManager.class, remap = false)
public interface GlStateManagerAccessor {
   @Accessor("BLEND")
   static BlendState getBLEND() {
      throw new UnsupportedOperationException("Not accessed");
   }

   @Accessor("BLEND_ENABLE")
   static boolean[] getBLEND_ENABLE() {
      throw new UnsupportedOperationException("Not accessed");
   }

   @Accessor("COLOR_MASK")
   static int[] getCOLOR_MASK() {
      throw new UnsupportedOperationException("Not accessed");
   }

   @Accessor("DEPTH")
   static DepthState getDEPTH() {
      throw new UnsupportedOperationException("Not accessed");
   }

   @Accessor("activeTexture")
   static int getActiveTexture() {
      throw new UnsupportedOperationException("Not accessed");
   }

   @Accessor("TEXTURES")
   static TextureState[] getTEXTURES() {
      throw new UnsupportedOperationException("Not accessed");
   }
}
