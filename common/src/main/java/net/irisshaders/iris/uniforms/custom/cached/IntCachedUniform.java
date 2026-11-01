package net.irisshaders.iris.uniforms.custom.cached;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.function.IntSupplier;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;

public class IntCachedUniform extends CachedUniform {
   private final IntSupplier supplier;
   private int cached;

   public IntCachedUniform(String name, UniformUpdateFrequency updateFrequency, IntSupplier supplier) {
      super(name, updateFrequency);
      this.supplier = supplier;
   }

   @Override
   protected boolean doUpdate() {
      int prev = this.cached;
      this.cached = this.supplier.getAsInt();
      return prev != this.cached;
   }

   @Override
   public void push(int location) {
      GlStateManager._glUniform1i(location, this.cached);
   }

   @Override
   public void writeTo(FunctionReturn functionReturn) {
      functionReturn.intReturn = this.cached;
   }

   @Override
   public Type getType() {
      return Type.Int;
   }
}
