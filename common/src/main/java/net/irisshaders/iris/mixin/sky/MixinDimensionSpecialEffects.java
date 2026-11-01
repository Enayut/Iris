package net.irisshaders.iris.mixin.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class MixinDimensionSpecialEffects {
   @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
   private void iris$getSunriseColor(RenderPass renderPass, PoseStack poseStack, float sunAngle, Vector4fc sunriseAndSunsetColor, CallbackInfo ci) {
      boolean blockSky = ((LevelRendererAccessor)Minecraft.getInstance().levelRenderer).getLevelRenderState().cameraRenderState.entityRenderState.doesMobEffectBlockSky;
      if (blockSky) {
         ci.cancel();
      }

      FogType fogType = Minecraft.getInstance().gameRenderer.mainCamera().getFluidInCamera();
      if (fogType != FogType.NONE) {
         ci.cancel();
      }
   }
}
