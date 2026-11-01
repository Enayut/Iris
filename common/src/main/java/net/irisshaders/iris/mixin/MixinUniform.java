package net.irisshaders.iris.mixin;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlStateManager.class)
public class MixinUniform {
   @Inject(method = "_glGetUniformLocation", at = @At("RETURN"), cancellable = true)
   private static void iris$glGetUniformLocation(int programId, CharSequence name, CallbackInfoReturnable<Integer> cir) {
      int location = (Integer)cir.getReturnValue();
      if (location == -1 && (name.equals("Sampler0") || name.equals("u_BlockTex"))) {
         location = GlStateManager._glGetUniformLocation(programId, "tex");
         if (location == -1) {
            location = GlStateManager._glGetUniformLocation(programId, "gtexture");
            if (location == -1) {
               location = GlStateManager._glGetUniformLocation(programId, "texture");
            }
         }
      }

      if (location == -1 && name.equals("Sampler1")) {
         location = GlStateManager._glGetUniformLocation(programId, "iris_overlay");
      }

      if (location == -1 && (name.equals("Sampler2") || name.equals("u_LightTex"))) {
         location = GlStateManager._glGetUniformLocation(programId, "lightmap");
      }

      if ((Integer)cir.getReturnValue() == -1 && location != -1) {
         cir.setReturnValue(location);
      }
   }
}
