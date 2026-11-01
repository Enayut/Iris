package net.irisshaders.iris.uniforms.custom.cached;

import java.util.function.Supplier;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.parsing.VectorType;
import org.joml.Vector3i;

public class Int3VectorCachedUniform extends VectorCachedUniform<Vector3i> {
   public Int3VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Vector3i> supplier) {
      super(name, updateFrequency, new Vector3i(), supplier);
   }

   protected void setFrom(Vector3i other) {
      this.cached.set(other);
   }

   @Override
   public void push(int location) {
      IrisRenderSystem.uniform3i(location, this.cached.x, this.cached.y, this.cached.z);
   }

   public VectorType getType() {
      return VectorType.I_VEC3;
   }
}
