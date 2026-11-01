package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.irisshaders.iris.Iris;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinTweakFarPlane {
   @Shadow
   private float renderDistance;

   @Shadow
   public float getDepthFar() {
      throw new AssertionError();
   }

   @Redirect(method = "getProjectionMatrix", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getDepthFar()F"))
   private float iris$tweakViewDistanceToMatchOptiFine(GameRenderer renderer) {
      if (Iris.getCurrentPack().isEmpty()) {
         return this.getDepthFar();
      }

      float tweakedViewDistance = this.renderDistance;
      return tweakedViewDistance + 1024.0F;
   }

   @Unique
   private void iris$tweakViewDistanceBasedOnFog(float f, long l, PoseStack poseStack, CallbackInfo ci) {
      if (!Iris.getCurrentPack().isEmpty()) {
         this.renderDistance *= 0.95F;
      }
   }
}
