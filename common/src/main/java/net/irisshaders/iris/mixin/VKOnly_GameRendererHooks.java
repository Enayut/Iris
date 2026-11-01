package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.irisshaders.iris.vulkan.IrisVulkanBackend;
import net.irisshaders.iris.vulkan.IrisVulkanRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vulkan-only frame hooks for the experimental screen-pass renderer: frame counters (as MixinGameRenderer does on
 * OpenGL), world depth capture before the hand pass clears it, and the screen passes after the level is rendered.
 */
@Mixin(GameRenderer.class)
public class VKOnly_GameRendererHooks {
   @Shadow
   @Final
   private Minecraft minecraft;
   @Shadow
   @Final
   private RenderTarget mainRenderTarget;

   @Inject(method = "render", at = @At("HEAD"))
   private void iris$vkStartFrame(CallbackInfo ci) {
      if (IrisVulkanBackend.EXPERIMENTAL_RENDERING) {
         CapturedRenderingState.INSTANCE.setRealTickDelta(this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true));
         SystemTimeUniforms.COUNTER.beginFrame();
         SystemTimeUniforms.TIMER.beginFrame(Util.getNanos());
      }
   }

   @Inject(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"
      )
   )
   private void iris$vkBeforeHud(CallbackInfo ci) {
      IrisVulkanRenderer.beforeHud(this.mainRenderTarget);
   }

   @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V", shift = At.Shift.AFTER))
   private void iris$vkScreenPasses(CallbackInfo ci) {
      IrisVulkanRenderer.afterLevel(this.mainRenderTarget);
   }
}
