package net.irisshaders.iris.gl.program;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerBinding;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.mixin.GlStateManagerAccessor;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;

public class ProgramSamplers {
   private static ProgramSamplers active;
   private final ImmutableList<SamplerBinding> samplerBindings;
   private final ImmutableList<ValueUpdateNotifier> notifiersToReset;
   private List<GlUniform1iCall> initializer;

   private ProgramSamplers(
      ImmutableList<SamplerBinding> samplerBindings, ImmutableList<ValueUpdateNotifier> notifiersToReset, List<GlUniform1iCall> initializer
   ) {
      this.samplerBindings = samplerBindings;
      this.notifiersToReset = notifiersToReset;
      this.initializer = initializer;
   }

   public static void clearActiveSamplers() {
      if (active != null) {
         active.removeListeners();
      }

      IrisRenderSystem.unbindAllSamplers();
   }

   public static ProgramSamplers.Builder builder(int program, Set<Integer> reservedTextureUnits) {
      return new ProgramSamplers.Builder(program, reservedTextureUnits);
   }

   public static ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor(
      SamplerHolder samplerHolder, Object2ObjectMap<String, TextureAccess> customTextureIds
   ) {
      return customTextureSamplerInterceptor(samplerHolder, customTextureIds, ImmutableSet.of());
   }

   public static ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor(
      SamplerHolder samplerHolder, Object2ObjectMap<String, TextureAccess> customTextureIds, ImmutableSet<Integer> flippedAtLeastOnceSnapshot
   ) {
      return new ProgramSamplers.CustomTextureSamplerInterceptor(samplerHolder, customTextureIds, flippedAtLeastOnceSnapshot);
   }

   public void update() {
      if (active != null) {
         active.removeListeners();
      }

      active = this;
      if (this.initializer != null) {
         for (GlUniform1iCall call : this.initializer) {
            GlStateManager._glUniform1i(call.location(), call.value());
         }

         this.initializer = null;
      }

      int activeTexture = GlStateManagerAccessor.getActiveTexture();
      UnmodifiableIterator var5 = this.samplerBindings.iterator();

      while (var5.hasNext()) {
         SamplerBinding samplerBinding = (SamplerBinding)var5.next();
         samplerBinding.update();
      }

      GlStateManager._activeTexture(33984 + activeTexture);
   }

   public void removeListeners() {
      active = null;
      UnmodifiableIterator var1 = this.notifiersToReset.iterator();

      while (var1.hasNext()) {
         ValueUpdateNotifier notifier = (ValueUpdateNotifier)var1.next();
         notifier.setListener(null);
      }
   }

   public static final class Builder implements SamplerHolder {
      private final int program;
      private final ImmutableSet<Integer> reservedTextureUnits;
      private final com.google.common.collect.ImmutableList.Builder<SamplerBinding> samplers;
      private final com.google.common.collect.ImmutableList.Builder<ValueUpdateNotifier> notifiersToReset;
      private final List<GlUniform1iCall> calls;
      private int remainingUnits;
      private int nextUnit;

      private Builder(int program, Set<Integer> reservedTextureUnits) {
         this.program = program;
         this.reservedTextureUnits = ImmutableSet.copyOf(reservedTextureUnits);
         this.samplers = ImmutableList.builder();
         this.notifiersToReset = ImmutableList.builder();
         this.calls = new ArrayList<>();
         int maxTextureUnits = SamplerLimits.get().getMaxTextureUnits();

         for (int unit : reservedTextureUnits) {
            if (unit >= maxTextureUnits) {
               throw new IllegalStateException(
                  "Cannot mark texture unit "
                     + unit
                     + " as reserved because that texture unit isn't available on this system! Only "
                     + maxTextureUnits
                     + " texture units are available."
               );
            }
         }

         this.remainingUnits = maxTextureUnits - reservedTextureUnits.size();

         while (reservedTextureUnits.contains(this.nextUnit)) {
            this.nextUnit++;
         }
      }

      @Override
      public void addExternalSampler(int textureUnit, String... names) {
         if (!this.reservedTextureUnits.contains(textureUnit)) {
            throw new IllegalArgumentException(
               "Cannot add an externally-managed sampler for texture unit " + textureUnit + " since it isn't in the set of reserved texture units."
            );
         }

         for (String name : names) {
            int location = GlStateManager._glGetUniformLocation(this.program, name);
            if (location != -1) {
               this.calls.add(new GlUniform1iCall(location, textureUnit));
            }
         }
      }

      @Override
      public boolean hasSampler(String name) {
         return GlStateManager._glGetUniformLocation(this.program, name) != -1;
      }

      @Override
      public boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, Supplier<GlSampler> sampler, String... names) {
         if (this.nextUnit != 0) {
            throw new IllegalStateException("Texture unit 0 is already used.");
         } else {
            return this.addDynamicSampler(TextureType.TEXTURE_2D, texture, sampler, true, notifier, names);
         }
      }

      @Override
      public boolean addDynamicSampler(TextureType type, IntSupplier texture, Supplier<GlSampler> sampler, String... names) {
         return this.addDynamicSampler(type, texture, sampler, false, null, names);
      }

      @Override
      public boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, Supplier<GlSampler> sampler, String... names) {
         return this.addDynamicSampler(type, texture, sampler, false, notifier, names);
      }

      private boolean addDynamicSampler(
         TextureType type, IntSupplier texture, Supplier<GlSampler> sampler, boolean used, ValueUpdateNotifier notifier, String... names
      ) {
         if (notifier != null) {
            this.notifiersToReset.add(notifier);
         }

         for (String name : names) {
            int location = GlStateManager._glGetUniformLocation(this.program, name);
            if (location != -1) {
               if (this.remainingUnits <= 0) {
                  throw new IllegalStateException("No more available texture units while activating sampler " + name);
               }

               this.calls.add(new GlUniform1iCall(location, this.nextUnit));
               used = true;
            }
         }

         if (!used) {
            return false;
         }

         this.samplers.add(new SamplerBinding(type, this.nextUnit, texture, sampler, notifier));
         this.remainingUnits--;
         this.nextUnit++;

         while (this.remainingUnits > 0 && this.reservedTextureUnits.contains(this.nextUnit)) {
            this.nextUnit++;
         }

         return true;
      }

      public ProgramSamplers build() {
         return new ProgramSamplers(this.samplers.build(), this.notifiersToReset.build(), this.calls);
      }
   }

   public static final class CustomTextureSamplerInterceptor implements SamplerHolder {
      private final SamplerHolder samplerHolder;
      private final Object2ObjectMap<String, TextureAccess> customTextureIds;
      private final ImmutableSet<String> deactivatedOverrides;

      private CustomTextureSamplerInterceptor(
         SamplerHolder samplerHolder, Object2ObjectMap<String, TextureAccess> customTextureIds, ImmutableSet<Integer> flippedAtLeastOnceSnapshot
      ) {
         this.samplerHolder = samplerHolder;
         this.customTextureIds = customTextureIds;
         com.google.common.collect.ImmutableSet.Builder<String> deactivatedOverrides = new com.google.common.collect.ImmutableSet.Builder();
         UnmodifiableIterator var5 = flippedAtLeastOnceSnapshot.iterator();

         while (var5.hasNext()) {
            int deactivatedOverride = (Integer)var5.next();
            deactivatedOverrides.add("colortex" + deactivatedOverride);
            if (deactivatedOverride < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
               deactivatedOverrides.add((String)PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(deactivatedOverride));
            }
         }

         this.deactivatedOverrides = deactivatedOverrides.build();
      }

      private TextureAccess getOverride(String... names) {
         for (String name : names) {
            if (this.customTextureIds.containsKey(name) && !this.deactivatedOverrides.contains(name)) {
               return (TextureAccess)this.customTextureIds.get(name);
            }
         }

         return null;
      }

      @Override
      public void addExternalSampler(int textureUnit, String... names) {
         TextureAccess override = this.getOverride(names);
         if (override != null) {
            if (textureUnit == 0) {
               this.samplerHolder.addDefaultSampler(override.getType(), override.getTextureId(), null, () -> override.getSampling(), names);
            } else {
               this.samplerHolder.addDynamicSampler(override.getType(), override.getTextureId(), null, () -> override.getSampling(), names);
            }
         } else {
            this.samplerHolder.addExternalSampler(textureUnit, names);
         }
      }

      @Override
      public boolean hasSampler(String name) {
         return this.samplerHolder.hasSampler(name);
      }

      @Override
      public boolean addDefaultSampler(IntSupplier sampler, String... names) {
         TextureAccess override = this.getOverride(names);
         return override != null
            ? this.samplerHolder.addDefaultSampler(override.getType(), override.getTextureId(), null, override::getSampling, names)
            : this.samplerHolder.addDefaultSampler(sampler, names);
      }

      @Override
      public boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, Supplier<GlSampler> sampler, String... names) {
         TextureAccess override = this.getOverride(names);
         return override != null
            ? this.samplerHolder.addDefaultSampler(override.getType(), override.getTextureId(), null, override::getSampling, names)
            : this.samplerHolder.addDefaultSampler(type, texture, notifier, sampler, names);
      }

      @Override
      public boolean addDynamicSampler(IntSupplier sampler, GlSampler glSampler, String... names) {
         TextureAccess override = this.getOverride(names);
         return override != null
            ? this.samplerHolder.addDynamicSampler(override.getType(), override.getTextureId(), null, override::getSampling, names)
            : this.samplerHolder.addDynamicSampler(sampler, glSampler, names);
      }

      @Override
      public boolean addDynamicSampler(TextureType type, IntSupplier texture, Supplier<GlSampler> sampler, String... names) {
         TextureAccess override = this.getOverride(names);
         return override != null
            ? this.samplerHolder.addDynamicSampler(override.getType(), override.getTextureId(), null, override::getSampling, names)
            : this.samplerHolder.addDynamicSampler(type, texture, sampler, names);
      }

      @Override
      public boolean addDynamicSampler(IntSupplier sampler, GlSampler glSampler, ValueUpdateNotifier notifier, String... names) {
         TextureAccess override = this.getOverride(names);
         return override != null
            ? this.samplerHolder.addDynamicSampler(override.getType(), override.getTextureId(), null, override::getSampling, names)
            : this.samplerHolder.addDynamicSampler(sampler, glSampler, notifier, names);
      }

      @Override
      public boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, Supplier<GlSampler> sampler, String... names) {
         return false;
      }
   }
}
