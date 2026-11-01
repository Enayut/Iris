package net.irisshaders.iris.pbr.mipmap;

import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.Nullable;

public interface CustomMipmapGenerator {
   NativeImage[] generateMipLevels(NativeImage[] var1, int var2);

   interface Provider {
      @Nullable
      CustomMipmapGenerator getMipmapGenerator();
   }
}
