package net.irisshaders.iris.mixin;

import net.irisshaders.iris.apiimpl.IrisApiV0Impl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
   @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
   private void fism$cancelGlintRendering(CallbackInfoReturnable<Boolean> cir) {
      if (IrisApiV0Impl.INSTANCE.isRenderingShadowPass()) {
         cir.setReturnValue(false);
      }
   }
}
