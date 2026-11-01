package net.irisshaders.iris.mixin;

import net.irisshaders.iris.vulkan.IrisVulkanRenderer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vulkan: the pack's {@code clouds=} directive overrides vanilla's cloud setting, as MixinOptions_CloudsOverride does
 * on OpenGL. Packs with their own clouds (Complementary draws them in deferred1) set {@code clouds=off}.
 */
@Mixin(value = Options.class, priority = 1010)
public class VKOnly_OptionsClouds {
   @Shadow
   @Final
   private OptionInstance<Integer> renderDistance;

   @Inject(method = "getCloudStatus", at = @At("HEAD"), cancellable = true)
   private void iris$vkOverrideCloudsType(CallbackInfoReturnable<CloudStatus> cir) {
      if (this.renderDistance.get() >= 4) {
         switch (IrisVulkanRenderer.cloudSetting()) {
            case OFF -> cir.setReturnValue(CloudStatus.OFF);
            case FAST -> cir.setReturnValue(CloudStatus.FAST);
            case FANCY -> cir.setReturnValue(CloudStatus.FANCY);
            case DEFAULT -> {
            }
         }
      }
   }
}
