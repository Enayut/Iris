package net.irisshaders.iris.uniforms.custom.cached;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.function.BooleanSupplier;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;

public class BooleanCachedUniform extends CachedUniform {
   private final BooleanSupplier supplier;
   private boolean cached;

   public BooleanCachedUniform(String name, UniformUpdateFrequency updateFrequency, BooleanSupplier supplier) {
      super(name, updateFrequency);
      this.supplier = supplier;
   }

   @Override
   protected boolean doUpdate() {
      boolean prev = this.cached;
      this.cached = this.supplier.getAsBoolean();
      return prev != this.cached;
   }

   @Override
   public void push(int location) {
      GlStateManager._glUniform1i(location, this.cached ? 1 : 0);
   }

   @Override
   public void writeTo(FunctionReturn functionReturn) {
      functionReturn.booleanReturn = this.cached;
   }

   @Override
   public Type getType() {
      return Type.Boolean;
   }
}
