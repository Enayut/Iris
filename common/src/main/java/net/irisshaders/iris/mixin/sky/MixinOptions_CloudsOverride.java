package net.irisshaders.iris.mixin.sky;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.properties.CloudSetting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Options.class, priority = 1010)
public class MixinOptions_CloudsOverride {
   @Shadow
   @Final
   private OptionInstance<Integer> renderDistance;

   @Inject(method = "getCloudStatus", at = @At("HEAD"), cancellable = true)
   private void iris$overrideCloudsType(CallbackInfoReturnable<CloudStatus> cir) {
      if ((Integer)this.renderDistance.get() >= 4) {
         Iris.getPipelineManager().getPipeline().ifPresent(p -> {
            CloudSetting setting = p.getCloudSetting();
            switch (setting) {
               case OFF:
                  cir.setReturnValue(CloudStatus.OFF);
                  return;
               case FAST:
                  cir.setReturnValue(CloudStatus.FAST);
                  return;
               case FANCY:
                  cir.setReturnValue(CloudStatus.FANCY);
            }
         });
      }
   }
}
