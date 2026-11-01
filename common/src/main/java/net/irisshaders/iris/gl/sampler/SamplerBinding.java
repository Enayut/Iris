package net.irisshaders.iris.gl.sampler;

import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.texture.TextureType;

public class SamplerBinding {
   private final int textureUnit;
   private final IntSupplier texture;
   private final ValueUpdateNotifier notifier;
   private final TextureType textureType;
   private final Supplier<GlSampler> sampler;

   public SamplerBinding(TextureType type, int textureUnit, IntSupplier texture, Supplier<GlSampler> sampler, ValueUpdateNotifier notifier) {
      this.textureType = type;
      this.textureUnit = textureUnit;
      this.texture = texture;
      this.sampler = sampler;
      this.notifier = notifier;
   }

   public void update() {
      this.updateSampler();
      if (this.notifier != null) {
         this.notifier.setListener(this::updateSampler);
      }
   }

   private void updateSampler() {
      GlSampler sampler2 = this.sampler == null ? null : this.sampler.get();
      IrisRenderSystem.bindSamplerToUnit(this.textureUnit, sampler2 == null ? 0 : sampler2.getId());
      IrisRenderSystem.bindTextureToUnit(this.textureType.getGlType(), this.textureUnit, this.texture.getAsInt());
   }
}
