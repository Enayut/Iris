package net.irisshaders.iris.targets.backed;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.nio.ByteBuffer;
import java.util.Random;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.texture.TextureUploadHelper;

public class NoiseTexture extends GlResource {
   int width;
   int height;

   public NoiseTexture(int width, int height) {
      super(IrisRenderSystem.createTexture(3553));
      int texture = this.getGlId();
      IrisRenderSystem.texParameteri(texture, 3553, 10241, 9729);
      IrisRenderSystem.texParameteri(texture, 3553, 10240, 9729);
      IrisRenderSystem.texParameteri(texture, 3553, 10242, 10497);
      IrisRenderSystem.texParameteri(texture, 3553, 10243, 10497);
      IrisRenderSystem.texParameteri(texture, 3553, 33085, 0);
      IrisRenderSystem.texParameteri(texture, 3553, 33082, 0);
      IrisRenderSystem.texParameteri(texture, 3553, 33083, 0);
      IrisRenderSystem.texParameterf(texture, 3553, 34049, 0.0F);
      this.resize(texture, width, height);
      GLDebug.nameObject(5890, texture, "noise texture");
      GlStateManager._bindTexture(0);
   }

   void resize(int texture, int width, int height) {
      this.width = width;
      this.height = height;
      ByteBuffer pixels = this.generateNoise();
      TextureUploadHelper.resetTextureUploadState();
      GlStateManager._pixelStore(3317, 1);
      IrisRenderSystem.texImage2D(texture, 3553, 0, 32849, width, height, 0, 6407, 5121, pixels);
      GlStateManager._bindTexture(0);
   }

   private ByteBuffer generateNoise() {
      byte[] pixels = new byte[3 * this.width * this.height];
      Random random = new Random(0L);
      random.nextBytes(pixels);
      ByteBuffer buffer = ByteBuffer.allocateDirect(pixels.length);
      buffer.put(pixels);
      buffer.flip();
      return buffer;
   }

   public int getTextureId() {
      return this.getGlId();
   }

   @Override
   protected void destroyInternal() {
      GlStateManager._deleteTexture(this.getGlId());
   }
}
