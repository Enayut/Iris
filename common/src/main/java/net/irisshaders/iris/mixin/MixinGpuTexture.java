package net.irisshaders.iris.mixin;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture.Usage;
import com.mojang.renderpearl.backend.common.BaseGpuTexture;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.irisshaders.iris.mixinterface.GpuTextureInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GlTexture.class)
public abstract class MixinGpuTexture extends BaseGpuTexture implements GpuTextureInterface {
   @Unique
   private boolean mipmapNonLinear;
   @Shadow
   @Final
   protected int id;

   public MixinGpuTexture(@Usage int usage, String label, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels) {
      super(usage, label, format, width, height, depthOrLayers, mipLevels);
   }

   @Shadow
   public abstract int glId();

   @Override
   public int iris$getGlId() {
      return this.glId();
   }

   @Override
   public void iris$markMipmapNonLinear() {
      this.mipmapNonLinear = true;
   }
}
