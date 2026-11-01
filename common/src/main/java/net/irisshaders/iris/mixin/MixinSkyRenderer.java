package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class MixinSkyRenderer {
   @Inject(method = "renderSkyDisc", at = @At("HEAD"))
   private void iris$renderSky$beginNormalSky(RenderPass renderPass, Vector3fc skyColor, CallbackInfo ci) {
      this.setPhase(WorldRenderingPhase.SKY);
   }

   @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
   private void iris$beforeDrawSun(RenderPass renderPass, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
      if (!Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::shouldRenderSun).orElse(true)) {
         ci.cancel();
      }
   }

   @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
   private void iris$beforeDrawMoon(RenderPass renderPass, MoonPhase moonPhase, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
      if (!Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::shouldRenderMoon).orElse(true)) {
         ci.cancel();
      }
   }

   @Inject(method = "renderSun", at = @At("HEAD"))
   private void iris$setSunRenderStage(RenderPass renderPass, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
      this.setPhase(WorldRenderingPhase.SUN);
   }

   @Inject(method = "renderMoon", at = @At("HEAD"))
   private void iris$setMoonRenderStage(RenderPass renderPass, MoonPhase moonPhase, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
      this.setPhase(WorldRenderingPhase.MOON);
   }

   @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"))
   private void iris$setSunsetRenderStage(RenderPass renderPass, PoseStack poseStack, float sunAngle, Vector4fc sunriseAndSunsetColor, CallbackInfo ci) {
      this.setPhase(WorldRenderingPhase.SUNSET);
   }

   @Inject(method = "renderStars", at = @At("HEAD"))
   private void iris$setStarRenderStage(RenderPass renderPass, float starBrightness, PoseStack poseStack, CallbackInfo ci) {
      this.setPhase(WorldRenderingPhase.STARS);
   }

   @Inject(method = "renderDarkDisc", at = @At("HEAD"))
   private void iris$setVoidRenderStage(CallbackInfo ci) {
      this.setPhase(WorldRenderingPhase.VOID);
   }

   @Inject(
      method = "renderSunMoonAndStars",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V", ordinal = 0, shift = Shift.AFTER)
   )
   private void iris$renderSky$tiltSun(
      RenderPass renderPass,
      PoseStack poseStack,
      float sunAngle,
      float moonAngle,
      float starAngle,
      MoonPhase moonPhase,
      float rainBrightness,
      float starBrightness,
      CallbackInfo ci
   ) {
      poseStack.rotateDegrees(Axis.ZP, this.getSunPathRotation());
   }

   private float getSunPathRotation() {
      return Iris.getPipelineManager().getPipelineNullable() == null ? 0.0F : Iris.getPipelineManager().getPipelineNullable().getSunPathRotation();
   }

   public void setPhase(WorldRenderingPhase phase) {
      if (Iris.getPipelineManager().getPipelineNullable() != null) {
         Iris.getPipelineManager().getPipelineNullable().setPhase(phase);
      }
   }
}
