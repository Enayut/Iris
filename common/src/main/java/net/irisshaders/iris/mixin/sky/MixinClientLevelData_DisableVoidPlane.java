package net.irisshaders.iris.mixin.sky;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel.ClientLevelData;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevelData.class)
public class MixinClientLevelData_DisableVoidPlane {
   @Inject(method = "getHorizonHeight", at = @At("HEAD"), cancellable = true)
   private void iris$getHorizonHeight(CallbackInfoReturnable<Double> cir) {
      FogType fogType = Minecraft.getInstance().gameRenderer.mainCamera().getFluidInCamera();
      if (fogType != FogType.NONE) {
         cir.setReturnValue(Double.NEGATIVE_INFINITY);
      }
   }
}
