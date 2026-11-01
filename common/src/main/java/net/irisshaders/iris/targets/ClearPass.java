package net.irisshaders.iris.targets;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.Objects;
import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import org.joml.Vector4f;

public class ClearPass {
   private final Vector4f color;
   private final IntSupplier viewportX;
   private final IntSupplier viewportY;
   private final GlFramebuffer framebuffer;
   private final int clearFlags;

   public ClearPass(Vector4f color, IntSupplier viewportX, IntSupplier viewportY, GlFramebuffer framebuffer, int clearFlags) {
      this.color = color;
      this.viewportX = viewportX;
      this.viewportY = viewportY;
      this.framebuffer = framebuffer;
      this.clearFlags = clearFlags;
   }

   public void execute(Vector4f defaultClearColor) {
      GlStateManager._viewport(0, 0, this.viewportX.getAsInt(), this.viewportY.getAsInt());
      this.framebuffer.bind();
      Vector4f color = Objects.requireNonNull(defaultClearColor);
      if (this.color != null) {
         color = this.color;
      }

      IrisRenderSystem.clearColor(color.x, color.y, color.z, color.w);
      GlStateManager._disableScissorTest();
      GlStateManager._clear(this.clearFlags);
   }

   public GlFramebuffer getFramebuffer() {
      return this.framebuffer;
   }
}
