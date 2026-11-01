package net.irisshaders.iris.gl.texture;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.mixin.GlStateManagerAccessor;
import org.lwjgl.opengl.GL;

public interface DepthCopyStrategy {
   static DepthCopyStrategy fastest(boolean combinedStencilRequired) {
      if (GL.getCapabilities().glCopyImageSubData != 0L) {
         return new DepthCopyStrategy.Gl43CopyImage();
      } else {
         return combinedStencilRequired ? new DepthCopyStrategy.Gl30BlitFbCombinedDepthStencil() : new DepthCopyStrategy.Gl20CopyTexture();
      }
   }

   boolean needsDestFramebuffer();

   void copy(GlFramebuffer var1, int var2, GlFramebuffer var3, int var4, int var5, int var6);

   class Gl20CopyTexture implements DepthCopyStrategy {
      private Gl20CopyTexture() {
      }

      @Override
      public boolean needsDestFramebuffer() {
         return false;
      }

      @Override
      public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
         sourceFb.bindAsReadBuffer();
         int previousTexture = GlStateManagerAccessor.getTEXTURES()[GlStateManagerAccessor.getActiveTexture()].binding;
         IrisRenderSystem.copyTexSubImage2D(destTexture, 3553, 0, 0, 0, 0, 0, width, height);
         GlStateManager._bindTexture(previousTexture);
      }
   }

   class Gl30BlitFbCombinedDepthStencil implements DepthCopyStrategy {
      private Gl30BlitFbCombinedDepthStencil() {
      }

      @Override
      public boolean needsDestFramebuffer() {
         return true;
      }

      @Override
      public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
         IrisRenderSystem.blitFramebuffer(sourceFb.getId(), destFb.getId(), 0, 0, width, height, 0, 0, width, height, 1280, 9728);
      }
   }

   class Gl43CopyImage implements DepthCopyStrategy {
      private Gl43CopyImage() {
      }

      @Override
      public boolean needsDestFramebuffer() {
         return false;
      }

      @Override
      public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
         IrisRenderSystem.copyImageSubData(sourceTexture, 3553, 0, 0, 0, 0, destTexture, 3553, 0, 0, 0, 0, width, height, 1);
      }
   }
}
