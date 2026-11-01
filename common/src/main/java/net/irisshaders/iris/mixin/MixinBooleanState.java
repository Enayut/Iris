package net.irisshaders.iris.mixin;

import com.mojang.renderpearl.backend.opengl.GlStateManager.BooleanState;
import net.irisshaders.iris.gl.BooleanStateExtended;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BooleanState.class)
public class MixinBooleanState implements BooleanStateExtended {
   @Shadow
   public boolean enabled;
   @Shadow
   @Final
   private int state;
   @Unique
   private boolean stateUnknown;

   @Inject(method = "setEnabled", at = @At("HEAD"), cancellable = true)
   private void iris$setUnknownState(boolean enable, CallbackInfo ci) {
      if (this.stateUnknown) {
         ci.cancel();
         this.enabled = enable;
         this.stateUnknown = false;
         if (enable) {
            GL11.glEnable(this.state);
         } else {
            GL11.glDisable(this.state);
         }
      }
   }

   @Override
   public void setUnknownState() {
      this.stateUnknown = true;
   }
}
