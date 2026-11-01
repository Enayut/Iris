package net.irisshaders.iris.mixin;

import net.irisshaders.iris.Iris;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer_Sky {
   @Inject(method = {"lambda$addSkyPass$0", "lambda$addSkyPass$8"}, require = 1, at = @At("HEAD"), cancellable = true)
   private static void preRenderSky(CallbackInfo ci) {
      if (Iris.getCurrentPack().isEmpty()) {
         Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
         Vec3 cameraPosition = camera.position();
         Entity cameraEntity = camera.entity();
         boolean isSubmersed = camera.getFluidInCamera() != FogType.NONE;
         boolean blockSky = ((LevelRendererAccessor)Minecraft.getInstance().levelRenderer).getLevelRenderState().cameraRenderState.entityRenderState.doesMobEffectBlockSky;
         boolean useThickFog = Minecraft.getInstance().gui.hud.getBossOverlay().shouldCreateWorldFog();
         if (isSubmersed || blockSky || useThickFog) {
            ci.cancel();
         }
      }
   }
}
