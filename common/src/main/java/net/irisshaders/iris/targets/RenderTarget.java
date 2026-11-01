package net.irisshaders.iris.targets;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.nio.ByteBuffer;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.PixelFormat;
import net.irisshaders.iris.gl.texture.PixelType;
import org.joml.Vector2i;

public class RenderTarget {
   private static final ByteBuffer NULL_BUFFER = null;
   private final InternalTextureFormat internalFormat;
   private final PixelFormat format;
   private final PixelType type;
   private final int mainTexture;
   private final int altTexture;
   private int width;
   private int height;
   private boolean isValid = true;
   private String name;
   private boolean allowsLinear;
   private boolean mipmapsOnAlt;
   private boolean mipmapsOnMain;

   public RenderTarget(RenderTarget.Builder builder) {
      this.name = builder.name;
      this.internalFormat = builder.internalFormat;
      this.format = builder.format;
      this.type = builder.type;
      this.width = builder.width;
      this.height = builder.height;
      this.mainTexture = GlStateManager._genTexture();
      this.altTexture = GlStateManager._genTexture();
      boolean isPixelFormatInteger = builder.internalFormat.getPixelFormat().isInteger();
      this.allowsLinear = !isPixelFormatInteger;
      this.setupTexture(this.mainTexture, builder.width, builder.height, !isPixelFormatInteger, false);
      this.setupTexture(this.altTexture, builder.width, builder.height, !isPixelFormatInteger, true);
      GlStateManager._bindTexture(0);
   }

   public static RenderTarget.Builder builder() {
      return new RenderTarget.Builder();
   }

   private void setupTexture(int texture, int width, int height, boolean allowsLinear, boolean alt) {
      this.resizeTexture(texture, width, height, alt);
      IrisRenderSystem.texParameteri(texture, 3553, 10241, allowsLinear ? 9729 : 9728);
      IrisRenderSystem.texParameteri(texture, 3553, 10240, allowsLinear ? 9729 : 9728);
      IrisRenderSystem.texParameteri(texture, 3553, 10242, 33071);
      IrisRenderSystem.texParameteri(texture, 3553, 10243, 33071);
   }

   private void resizeTexture(int texture, int width, int height, boolean alt) {
      IrisRenderSystem.texImage2D(
         texture, 3553, 0, this.internalFormat.getGlFormat(), width, height, 0, this.format.getGlFormat(), this.type.getGlFormat(), NULL_BUFFER
      );
      if (this.name != null) {
         GLDebug.nameObject(5890, texture, this.name + " " + (alt ? "alt" : "main"));
      }
   }

   void resize(Vector2i textureScaleOverride) {
      this.resize(textureScaleOverride.x, textureScaleOverride.y);
   }

   void resize(int width, int height) {
      this.requireValid();
      this.width = width;
      this.height = height;
      this.resizeTexture(this.mainTexture, width, height, false);
      this.resizeTexture(this.altTexture, width, height, true);
   }

   public InternalTextureFormat getInternalFormat() {
      return this.internalFormat;
   }

   public int getMainTexture() {
      this.requireValid();
      return this.mainTexture;
   }

   public int getAltTexture() {
      this.requireValid();
      return this.altTexture;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public void destroy() {
      this.requireValid();
      this.isValid = false;
      GlStateManager._deleteTexture(this.mainTexture);
      GlStateManager._deleteTexture(this.altTexture);
   }

   private void requireValid() {
      if (!this.isValid) {
         throw new IllegalStateException("Attempted to use a deleted composite render target");
      }
   }

   public GlSampler getAltSampler() {
      if (this.mipmapsOnAlt) {
         return this.allowsLinear ? GlSampler.MIPPED_LINEAR : GlSampler.MIPPED_NEAREST;
      } else {
         return this.allowsLinear ? GlSampler.LINEAR : GlSampler.NEAREST;
      }
   }

   public GlSampler getMainSampler() {
      if (this.mipmapsOnMain) {
         return this.allowsLinear ? GlSampler.MIPPED_LINEAR : GlSampler.MIPPED_NEAREST;
      } else {
         return this.allowsLinear ? GlSampler.LINEAR : GlSampler.NEAREST;
      }
   }

   public void turnOnMips(boolean alt) {
      if (alt) {
         this.mipmapsOnAlt = true;
      } else {
         this.mipmapsOnMain = true;
      }
   }

   public void turnOffMips(boolean alt) {
      if (alt) {
         this.mipmapsOnAlt = false;
      } else {
         this.mipmapsOnMain = false;
      }
   }

   public static class Builder {
      private InternalTextureFormat internalFormat = InternalTextureFormat.RGBA8;
      private int width = 0;
      private int height = 0;
      private PixelFormat format = PixelFormat.RGBA;
      private PixelType type = PixelType.UNSIGNED_BYTE;
      private String name = null;

      private Builder() {
      }

      public RenderTarget.Builder setName(String name) {
         this.name = name;
         return this;
      }

      public RenderTarget.Builder setInternalFormat(InternalTextureFormat format) {
         this.internalFormat = format;
         return this;
      }

      public RenderTarget.Builder setDimensions(int width, int height) {
         if (width <= 0) {
            throw new IllegalArgumentException("Width must be greater than zero");
         }

         if (height <= 0) {
            throw new IllegalArgumentException("Height must be greater than zero");
         }

         this.width = width;
         this.height = height;
         return this;
      }

      public RenderTarget.Builder setPixelFormat(PixelFormat pixelFormat) {
         this.format = pixelFormat;
         return this;
      }

      public RenderTarget.Builder setPixelType(PixelType pixelType) {
         this.type = pixelType;
         return this;
      }

      public RenderTarget build() {
         return new RenderTarget(this);
      }
   }
}
