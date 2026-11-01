package net.irisshaders.iris.gl.uniform;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.function.Supplier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL46C;

public class MatrixFromFloatArrayUniform extends Uniform {
   private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
   private final Supplier<float[]> value;
   private float[] cachedValue = null;

   MatrixFromFloatArrayUniform(int location, Supplier<float[]> value) {
      super(location);
      this.value = value;
   }

   @Override
   public void update() {
      float[] newValue = this.value.get();
      if (!Arrays.equals(newValue, this.cachedValue)) {
         this.cachedValue = Arrays.copyOf(newValue, 16);
         this.buffer.put(this.cachedValue);
         this.buffer.rewind();
         GL46C.glUniformMatrix4fv(this.location, false, this.buffer);
      }
   }
}
