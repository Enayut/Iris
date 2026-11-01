package net.irisshaders.iris.mixin;

import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft_Keybinds {
   @Inject(method = "tick()V", at = @At("RETURN"))
   private void iris$onTick(CallbackInfo ci) {
      Profiler.get().push("iris_keybinds");
      Iris.handleKeybinds((Minecraft)(Object)this);
      Profiler.get().pop();
   }
}
