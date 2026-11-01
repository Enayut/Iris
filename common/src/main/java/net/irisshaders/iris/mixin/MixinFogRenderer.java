package net.irisshaders.iris.mixin;

import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
   @Inject(method = "setupFog", at = @At("HEAD"))
   private void iris$setupLegacyWaterFog(Camera camera, int i, DeltaTracker deltaTracker, float f, ClientLevel clientLevel, CallbackInfoReturnable<FogData> cir) {
      if (camera.getFluidInCamera() == FogType.WATER) {
         Entity entity = camera.entity();
         float density = 0.05F;
         if (entity instanceof LocalPlayer localPlayer) {
            density -= localPlayer.getWaterVision() * localPlayer.getWaterVision() * 0.03F;
            Holder var10 = localPlayer.level().getBiome(localPlayer.blockPosition());
         }

         CapturedRenderingState.INSTANCE.setFogDensity(density);
      } else {
         CapturedRenderingState.INSTANCE.setFogDensity(-1.0F);
      }
   }

   @Inject(method = "setupFog", at = @At("RETURN"))
   private void render(Camera camera, int i, DeltaTracker deltaTracker, float f, ClientLevel clientLevel, CallbackInfoReturnable<FogData> cir) {
      CapturedRenderingState.INSTANCE
         .setFogColor(((FogData)cir.getReturnValue()).color.x, ((FogData)cir.getReturnValue()).color.y, ((FogData)cir.getReturnValue()).color.z);
   }
}
