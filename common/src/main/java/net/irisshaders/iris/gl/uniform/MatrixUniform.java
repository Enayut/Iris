package net.irisshaders.iris.gl.uniform;

import java.nio.FloatBuffer;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL46C;

public class MatrixUniform extends Uniform {
   private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
   private final Supplier<Matrix4fc> value;
   private final Matrix4f cachedValue = new Matrix4f();

   MatrixUniform(int location, Supplier<Matrix4fc> value) {
      super(location);
      this.value = value;
   }

   MatrixUniform(int location, Supplier<Matrix4fc> value, ValueUpdateNotifier notifier) {
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
      Matrix4fc newValue = this.value.get();
      if (!this.cachedValue.equals(newValue)) {
         this.cachedValue.set(newValue);
         this.cachedValue.get(this.buffer);
         this.buffer.rewind();
         GL46C.glUniformMatrix4fv(this.location, false, this.buffer);
      }
   }
}
