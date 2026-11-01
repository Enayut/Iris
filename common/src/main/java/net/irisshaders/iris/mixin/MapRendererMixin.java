package net.irisshaders.iris.mixin;

import net.irisshaders.iris.apiimpl.IrisApiV0Impl;
import net.minecraft.client.renderer.MapRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MapRenderer.class)
public abstract class MapRendererMixin {
   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   public <S> void fism$cancelMapRendering(CallbackInfo ci) {
      if (IrisApiV0Impl.INSTANCE.isRenderingShadowPass()) {
         ci.cancel();
      }
   }
}
