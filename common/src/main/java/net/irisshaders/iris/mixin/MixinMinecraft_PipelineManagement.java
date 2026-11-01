package net.irisshaders.iris.mixin;

import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft_PipelineManagement {
   @Inject(method = "clearClientLevel", at = @At("HEAD"))
   public void iris$trackLastDimensionOnLeave(Screen arg, CallbackInfo ci) {
      Iris.lastDimension = Iris.getCurrentDimension();
   }

   @Inject(method = "setLevel", at = @At("HEAD"))
   private void iris$trackLastDimensionOnLevelChange(ClientLevel clientLevel, CallbackInfo ci) {
      Iris.lastDimension = Iris.getCurrentDimension();
   }

   @Inject(method = "updateLevelInEngines", at = @At("HEAD"))
   private void iris$resetPipeline(@Nullable ClientLevel level, CallbackInfo ci) {
      if (Iris.getCurrentDimension() != Iris.lastDimension) {
         Iris.logger.info("Reloading pipeline on dimension change: " + Iris.lastDimension + " => " + Iris.getCurrentDimension());
         Iris.getPipelineManager().destroyPipeline();
         if (level != null) {
            Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());
         }
      }
   }
}
