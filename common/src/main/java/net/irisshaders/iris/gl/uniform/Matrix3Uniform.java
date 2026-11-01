package net.irisshaders.iris.gl.uniform;

import java.nio.FloatBuffer;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.lwjgl.BufferUtils;

public class Matrix3Uniform extends Uniform {
   private final FloatBuffer buffer = BufferUtils.createFloatBuffer(9);
   private final Supplier<Matrix3fc> value;
   private final Matrix3f cachedValue = new Matrix3f();

   Matrix3Uniform(int location, Supplier<Matrix3fc> value) {
      super(location);
      this.value = value;
   }

   Matrix3Uniform(int location, Supplier<Matrix3fc> value, ValueUpdateNotifier notifier) {
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

   public void updateValue() {
      Matrix3fc newValue = this.value.get();
      if (!this.cachedValue.equals(newValue)) {
         this.cachedValue.set(newValue);
         this.cachedValue.get(this.buffer);
         this.buffer.rewind();
         IrisRenderSystem.uniformMatrix3fv(this.location, false, this.buffer);
      }
   }
}
