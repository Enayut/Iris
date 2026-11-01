package net.irisshaders.iris.gl.sampler;

import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.texture.TextureType;

public interface SamplerHolder {
   void addExternalSampler(int var1, String... var2);

   boolean hasSampler(String var1);

   default boolean addDefaultSampler(IntSupplier sampler, String... names) {
      return this.addDefaultSampler(TextureType.TEXTURE_2D, sampler, null, null, names);
   }

   boolean addDefaultSampler(TextureType var1, IntSupplier var2, ValueUpdateNotifier var3, Supplier<GlSampler> var4, String... var5);

   default boolean addDynamicSampler(IntSupplier texture, GlSampler sampler, String... names) {
      return this.addDynamicSampler(TextureType.TEXTURE_2D, texture, () -> sampler, names);
   }

   boolean addDynamicSampler(TextureType var1, IntSupplier var2, Supplier<GlSampler> var3, String... var4);

   default boolean addDynamicSampler(IntSupplier texture, GlSampler sampler, ValueUpdateNotifier notifier, String... names) {
      return this.addDynamicSampler(TextureType.TEXTURE_2D, texture, notifier, () -> sampler, names);
   }

   boolean addDynamicSampler(TextureType var1, IntSupplier var2, ValueUpdateNotifier var3, Supplier<GlSampler> var4, String... var5);
}
