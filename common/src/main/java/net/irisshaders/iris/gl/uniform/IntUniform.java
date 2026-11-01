package net.irisshaders.iris.gl.uniform;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;

public class IntUniform extends Uniform {
   private final IntSupplier value;
   private int cachedValue = 0;

   IntUniform(int location, IntSupplier value) {
      this(location, value, null);
   }

   IntUniform(int location, IntSupplier value, ValueUpdateNotifier notifier) {
      super(location, notifier);
      this.value = value;
   }

   @Override
   public void update() {
      this.updateValue();
      if (this.notifier != null) {
         this.notifier.setListener(this::updateValue);
      }
   }

   private void updateValue() {
      int newValue = this.value.getAsInt();
      if (this.cachedValue != newValue) {
         this.cachedValue = newValue;
         GlStateManager._glUniform1i(this.location, newValue);
      }
   }
}
