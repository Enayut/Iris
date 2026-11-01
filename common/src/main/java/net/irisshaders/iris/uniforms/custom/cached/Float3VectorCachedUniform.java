package net.irisshaders.iris.uniforms.custom.cached;

import java.util.function.Supplier;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.parsing.VectorType;
import org.joml.Vector3f;

public class Float3VectorCachedUniform extends VectorCachedUniform<Vector3f> {
   public Float3VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Vector3f> supplier) {
      super(name, updateFrequency, new Vector3f(), supplier);
   }

   protected void setFrom(Vector3f other) {
      this.cached.set(other);
   }

   @Override
   public void push(int location) {
      IrisRenderSystem.uniform3f(location, this.cached.x, this.cached.y, this.cached.z);
   }

   public VectorType getType() {
      return VectorType.VEC3;
   }
}
