package net.irisshaders.iris.mixin.gui;

import net.irisshaders.iris.vulkan.IrisVulkanRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vulkan: vanilla's vignette follows the pack's {@code vignette} directive, as MixinHud does on OpenGL. */
@Mixin(Hud.class)
public class VKOnly_HudVignette {
   @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
   private void iris$vkDisableVignetteRendering(GuiGraphicsExtractor pGui0, Entity pEntity1, CallbackInfo ci) {
      if (!IrisVulkanRenderer.shouldRenderVignette()) {
         ci.cancel();
      }
   }
}
