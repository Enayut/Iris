package net.irisshaders.iris.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.Options.FieldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Options.class)
public abstract class MixinMaxFpsCrashFix {
   @Unique
   private void iris$resetFramerateLimit(FieldAccess instance, String name, OptionInstance<Integer> option) {
      if ((Integer)option.get() == 0) {
         option.set(120);
      }

      instance.process(name, option);
   }
}
