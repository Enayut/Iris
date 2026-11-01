package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.irisshaders.iris.vulkan.IrisVulkanRenderer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState.WaterOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vulkan: vanilla's underwater overlay follows the pack's {@code underwaterOverlay} directive, as
 * MixinScreenEffectRenderer does on OpenGL.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class VKOnly_ScreenEffectRendererWater {
   @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
   private static void iris$vkDisableUnderWaterOverlayRendering(
      WaterOverlay waterOverlay, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci
   ) {
      if (!IrisVulkanRenderer.shouldRenderUnderwaterOverlay()) {
         ci.cancel();
      }
   }
}
