package net.irisshaders.iris.mixin;

import net.irisshaders.iris.apiimpl.IrisApiV0Impl;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignRenderer.class)
public abstract class AbstractSignRendererMixin {
   @Inject(method = "submitSignText", at = @At("HEAD"), cancellable = true)
   public void fism$cancelSignTextRendering(CallbackInfo ci) {
      if (IrisApiV0Impl.INSTANCE.isRenderingShadowPass()) {
         ci.cancel();
      }
   }
}
