package net.irisshaders.iris.uniforms.custom.cached;

import java.util.function.Supplier;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.parsing.MatrixType;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;

public class Float3MatrixCachedUniform extends VectorCachedUniform<Matrix3fc> {
   private final float[] buffer = new float[9];

   public Float3MatrixCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Matrix3fc> supplier) {
      super(name, updateFrequency, new Matrix3f(), supplier);
   }

   protected void setFrom(Matrix3fc other) {
      ((Matrix3f)this.cached).set(other);
   }

   @Override
   public void push(int location) {
      this.cached.get(this.buffer);
      IrisRenderSystem.uniformMatrix3fv(location, false, this.buffer);
   }

   public MatrixType<Matrix3f> getType() {
      return MatrixType.MAT3;
   }
}
