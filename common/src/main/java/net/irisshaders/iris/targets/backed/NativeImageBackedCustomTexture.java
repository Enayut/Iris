package net.irisshaders.iris.targets.backed;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.shaderpack.texture.CustomTextureData;
import net.minecraft.client.renderer.texture.DynamicTexture;

public class NativeImageBackedCustomTexture extends DynamicTexture implements TextureAccess {
   private final boolean shouldBlur;
   private final boolean shouldClamp;

   public NativeImageBackedCustomTexture(CustomTextureData.PngData textureData) throws IOException {
      super(() -> "PNG Texture", create(textureData.getContent()));
      if (textureData.getFilteringData().shouldBlur()) {
         IrisRenderSystem.texParameteri(this.getId(), 3553, 10241, 9729);
         IrisRenderSystem.texParameteri(this.getId(), 3553, 10240, 9729);
      }

      if (textureData.getFilteringData().shouldClamp()) {
         IrisRenderSystem.texParameteri(this.getId(), 3553, 10242, 33071);
         IrisRenderSystem.texParameteri(this.getId(), 3553, 10243, 33071);
      }

      this.shouldBlur = textureData.getFilteringData().shouldBlur();
      this.shouldClamp = textureData.getFilteringData().shouldClamp();
   }

   private int getId() {
      return this.texture.iris$getGlId();
   }

   private static NativeImage create(byte[] content) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocateDirect(content.length);
      buffer.put(content);
      buffer.flip();
      return NativeImage.read(buffer);
   }

   public void upload() {
      NativeImage image = Objects.requireNonNull(this.getPixels());
      RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, image);
   }

   @Override
   public TextureType getType() {
      return TextureType.TEXTURE_2D;
   }

   @Override
   public IntSupplier getTextureId() {
      return this::getId;
   }

   @Override
   public GlSampler getSampling() {
      if (this.shouldClamp) {
         return this.shouldBlur ? GlSampler.LINEAR : GlSampler.NEAREST;
      } else {
         return this.shouldBlur ? GlSampler.LINEAR_REPEAT : GlSampler.NEAREST_REPEAT;
      }
   }
}
