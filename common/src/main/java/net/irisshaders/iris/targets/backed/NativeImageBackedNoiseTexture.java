package net.irisshaders.iris.targets.backed;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Objects;
import java.util.Random;
import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.gl.texture.TextureType;
import net.minecraft.client.renderer.texture.DynamicTexture;

public class NativeImageBackedNoiseTexture extends DynamicTexture implements TextureAccess {
   public NativeImageBackedNoiseTexture(int size) {
      super(() -> "Noise / " + size, create(size));
   }

   private static NativeImage create(int size) {
      NativeImage image = new NativeImage(Format.RGBA, size, size, false);
      Random random = new Random(0L);

      for (int x = 0; x < size; x++) {
         for (int y = 0; y < size; y++) {
            int color = random.nextInt() | 0xFF000000;
            image.setPixel(x, y, color);
         }
      }

      return image;
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
      return this.getTexture()::iris$getGlId;
   }

   @Override
   public GlSampler getSampling() {
      return GlSampler.LINEAR_REPEAT;
   }
}
