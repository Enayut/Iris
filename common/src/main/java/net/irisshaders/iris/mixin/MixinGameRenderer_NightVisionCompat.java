package net.irisshaders.iris.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameRenderer.class, priority = 1010)
public class MixinGameRenderer_NightVisionCompat {
   @Inject(
      method = "nightVisionScale",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffectInstance;endsWithin(I)Z"),
      cancellable = true,
      require = 0
   )
   private static void iris$safecheckNightvisionStrength(LivingEntity livingEntity, float partialTicks, CallbackInfoReturnable<Float> cir) {
      if (livingEntity.getEffect(MobEffects.NIGHT_VISION) == null) {
         cir.setReturnValue(0.0F);
      }
   }
}
