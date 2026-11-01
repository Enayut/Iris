package net.irisshaders.iris.samplers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.state.StateUpdateNotifiers;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.minecraft.client.renderer.texture.AbstractTexture;

public class IrisSamplers {
   public static final int ALBEDO_TEXTURE_UNIT = 0;
   public static final int OVERLAY_TEXTURE_UNIT = 1;
   public static final int LIGHTMAP_TEXTURE_UNIT = 2;
   public static final ImmutableSet<Integer> WORLD_RESERVED_TEXTURE_UNITS = ImmutableSet.of(0, 1, 2, 3);
   public static final ImmutableSet<Integer> SODIUM_RESERVED_TEXTURE_UNITS = ImmutableSet.of(0, 2);
   public static final ImmutableSet<Integer> COMPOSITE_RESERVED_TEXTURE_UNITS = ImmutableSet.of();
   private static final GpuSampler[] terrain = new GpuSampler[16];
   private static final GlSampler[] terrainS = new GlSampler[16];

   private IrisSamplers() {
   }

   public static void initRenderer() {
   }

   public static void addRenderTargetSamplers(
      SamplerHolder samplers, Supplier<ImmutableSet<Integer>> flipped, RenderTargets renderTargets, boolean isFullscreenPass, WorldRenderingPipeline pipeline
   ) {
      int startIndex = isFullscreenPass ? 0 : 4;

      for (int i = startIndex; i < renderTargets.getRenderTargetCount(); i++) {
         int index = i;
         IntSupplier texture = () -> {
            ImmutableSet<Integer> flippedBuffers = flipped.get();
            RenderTarget target = renderTargets.getOrCreate(index);
            return flippedBuffers.contains(index) ? target.getAltTexture() : target.getMainTexture();
         };
         Supplier<GlSampler> sampler = () -> {
            ImmutableSet<Integer> flippedBuffers = flipped.get();
            RenderTarget target = renderTargets.getOrCreate(index);
            return flippedBuffers.contains(index) ? target.getAltSampler() : target.getMainSampler();
         };
         String name = "colortex" + i;
         if (i < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
            String legacyName = (String)PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(i);
            if (samplers.hasSampler(legacyName) || samplers.hasSampler(name)) {
               renderTargets.createIfUnsure(index);
            }

            if (i == 0 && isFullscreenPass) {
               samplers.addDefaultSampler(TextureType.TEXTURE_2D, texture, null, sampler, name, legacyName);
            } else {
               samplers.addDynamicSampler(TextureType.TEXTURE_2D, texture, sampler, name, legacyName);
            }
         } else {
            if (samplers.hasSampler(name)) {
               renderTargets.createIfUnsure(index);
            }

            samplers.addDynamicSampler(TextureType.TEXTURE_2D, texture, sampler, name);
         }
      }

      samplers.addDynamicSampler(TextureType.TEXTURE_2D, () -> pipeline.getDHCompat().getDepthTex(), null, "dhDepthTex", "dhDepthTex0");
      samplers.addDynamicSampler(TextureType.TEXTURE_2D, () -> pipeline.getDHCompat().getDepthTexNoTranslucent(), null, "dhDepthTex1");
   }

   public static void addNoiseSampler(SamplerHolder samplers, TextureAccess sampler) {
      samplers.addDynamicSampler(sampler.getTextureId(), GlSampler.LINEAR_REPEAT, "noisetex");
   }

   public static boolean hasShadowSamplers(SamplerHolder samplers) {
      Builder<String> shadowSamplers = ImmutableList.<String>builder()
         .add("shadowtex0", "shadowtex0DH", "shadowtex0HW", "shadowtex1", "shadowtex1HW", "shadowtex1DH", "shadow", "watershadow", "shadowcolor");

      for (int i = 0; i < 8; i++) {
         shadowSamplers.add("shadowcolor" + i);
         shadowSamplers.add("shadowcolorimg" + i);
      }

      UnmodifiableIterator var4 = shadowSamplers.build().iterator();

      while (var4.hasNext()) {
         String samplerName = (String)var4.next();
         if (samplers.hasSampler(samplerName)) {
            return true;
         }
      }

      return false;
   }

   public static boolean addShadowSamplers(
      SamplerHolder samplers, ShadowRenderTargets shadowRenderTargets, ImmutableSet<Integer> flipped, boolean separateHardwareSamplers
   ) {
      boolean waterShadowEnabled = samplers.hasSampler("watershadow");
      boolean usesShadows;
      if (waterShadowEnabled) {
         usesShadows = true;
         samplers.addDynamicSampler(
            TextureType.TEXTURE_2D,
            () -> shadowRenderTargets.getDepthTexture().iris$getGlId(),
            () -> separateHardwareSamplers ? null : shadowRenderTargets.getSamplerFor(0),
            "shadowtex0",
            "watershadow"
         );
         samplers.addDynamicSampler(
            TextureType.TEXTURE_2D,
            () -> shadowRenderTargets.getDepthTextureNoTranslucents().iris$getGlId(),
            () -> separateHardwareSamplers ? null : shadowRenderTargets.getSamplerFor(1),
            "shadowtex1",
            "shadow"
         );
      } else {
         usesShadows = samplers.addDynamicSampler(
            TextureType.TEXTURE_2D,
            () -> shadowRenderTargets.getDepthTexture().iris$getGlId(),
            () -> separateHardwareSamplers ? null : shadowRenderTargets.getSamplerFor(0),
            "shadowtex0",
            "shadow"
         );
         usesShadows |= samplers.addDynamicSampler(
            TextureType.TEXTURE_2D,
            () -> shadowRenderTargets.getDepthTextureNoTranslucents().iris$getGlId(),
            () -> separateHardwareSamplers ? null : shadowRenderTargets.getSamplerFor(1),
            "shadowtex1"
         );
      }

      if (flipped == null) {
         if (samplers.addDynamicSampler(() -> shadowRenderTargets.getColorTextureId(0), GlSampler.LINEAR, "shadowcolor")) {
            shadowRenderTargets.createIfEmpty(0);
         }

         for (int i = 0; i < shadowRenderTargets.getRenderTargetCount(); i++) {
            int finalI = i;
            if (samplers.addDynamicSampler(() -> shadowRenderTargets.getColorTextureId(finalI), GlSampler.LINEAR, "shadowcolor" + i)) {
               shadowRenderTargets.createIfEmpty(finalI);
            }
         }
      } else {
         if (samplers.addDynamicSampler(
            () -> flipped.contains(0) ? shadowRenderTargets.get(0).getAltTexture() : shadowRenderTargets.get(0).getMainTexture(),
            GlSampler.LINEAR,
            "shadowcolor"
         )) {
            shadowRenderTargets.createIfEmpty(0);
         }

         for (int i = 0; i < shadowRenderTargets.getRenderTargetCount(); i++) {
            int finalI = i;
            if (samplers.addDynamicSampler(
               () -> flipped.contains(finalI) ? shadowRenderTargets.get(finalI).getAltTexture() : shadowRenderTargets.get(finalI).getMainTexture(),
               GlSampler.LINEAR,
               "shadowcolor" + i
            )) {
               shadowRenderTargets.createIfEmpty(finalI);
            }
         }
      }

      if (shadowRenderTargets.isHardwareFiltered(0) && separateHardwareSamplers) {
         samplers.addDynamicSampler(
            TextureType.TEXTURE_2D, () -> shadowRenderTargets.getDepthTexture().iris$getGlId(), () -> shadowRenderTargets.getSamplerFor(0), "shadowtex0HW"
         );
      }

      if (shadowRenderTargets.isHardwareFiltered(1) && separateHardwareSamplers) {
         samplers.addDynamicSampler(
            TextureType.TEXTURE_2D,
            () -> shadowRenderTargets.getDepthTextureNoTranslucents().iris$getGlId(),
            () -> shadowRenderTargets.getSamplerFor(1),
            "shadowtex1HW"
         );
      }

      return usesShadows;
   }

   public static boolean hasPBRSamplers(SamplerHolder samplers) {
      return samplers.hasSampler("normals") || samplers.hasSampler("specular");
   }

   public static void addLevelSamplers(
      SamplerHolder samplers, WorldRenderingPipeline pipeline, AbstractTexture whitePixel, boolean hasTexture, boolean hasLightmap, boolean hasOverlay
   ) {
      if (hasTexture) {
         samplers.addExternalSampler(0, "tex", "texture", "gtexture", "u_MainSampler");
      } else {
         samplers.addDynamicSampler(
            () -> whitePixel.getTexture().iris$getGlId(), GlSampler.NEAREST, "tex", "texture", "gtexture", "u_MainSampler", "gcolor", "colortex0"
         );
      }

      if (hasLightmap) {
         samplers.addExternalSampler(2, "lightmap");
      } else {
         samplers.addDynamicSampler(() -> whitePixel.getTexture().iris$getGlId(), GlSampler.NEAREST, "lightmap");
      }

      if (hasOverlay) {
         samplers.addExternalSampler(1, "iris_overlay");
      } else {
         samplers.addDynamicSampler(() -> whitePixel.getTexture().iris$getGlId(), GlSampler.NEAREST, "iris_overlay");
      }

      if (pipeline instanceof IrisRenderingPipeline irp) {
         samplers.addDynamicSampler(
            TextureType.TEXTURE_2D, pipeline::getCurrentNormalTexture, StateUpdateNotifiers.normalTextureChangeNotifier, irp::getNormalSampler, "normals"
         );
         samplers.addDynamicSampler(
            TextureType.TEXTURE_2D,
            pipeline::getCurrentSpecularTexture,
            StateUpdateNotifiers.specularTextureChangeNotifier,
            irp::getSpecularSampler,
            "specular"
         );
      }
   }

   public static void addWorldDepthSamplers(SamplerHolder samplers, RenderTargets renderTargets) {
      samplers.addDynamicSampler(() -> renderTargets.getDepthTexture().iris$getGlId(), GlSampler.NEAREST, "depthtex0");
      samplers.addDynamicSampler(() -> renderTargets.getDepthTextureNoTranslucents().iris$getGlId(), GlSampler.NEAREST, "depthtex1");
      samplers.addDynamicSampler(() -> renderTargets.getDepthTextureNoHand().iris$getGlId(), GlSampler.NEAREST, "depthtex2");
   }

   public static void addCompositeSamplers(SamplerHolder samplers, RenderTargets renderTargets) {
      samplers.addDynamicSampler(() -> renderTargets.getDepthTexture().iris$getGlId(), GlSampler.NEAREST, "gdepthtex", "depthtex0");
      samplers.addDynamicSampler(() -> renderTargets.getDepthTextureNoTranslucents().iris$getGlId(), GlSampler.NEAREST, "depthtex1");
      samplers.addDynamicSampler(() -> renderTargets.getDepthTextureNoHand().iris$getGlId(), GlSampler.NEAREST, "depthtex2");
   }

   public static void addCustomTextures(SamplerHolder samplers, Object2ObjectMap<String, TextureAccess> irisCustomTextures) {
      irisCustomTextures.forEach((name, texture) -> samplers.addDynamicSampler(texture.getType(), texture.getTextureId(), () -> texture.getSampling(), name));
   }

   public static void addCustomImages(SamplerHolder images, Set<GlImage> customImages) {
      customImages.forEach(image -> {
         if (image.getSamplerName() != null) {
            images.addDynamicSampler(image.getTarget(), image::getId, null, image.getSamplerName());
         }
      });
   }

   public static GpuSampler getTerrainCache(int i) {
      if (WorldRenderingSettings.INSTANCE.breaksAnisotropy()) {
         i = 1;
      }

      if (terrain[i] == null) {
         terrain[i] = RenderSystem.getDevice()
            .createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, i, OptionalDouble.empty());
      }

      return terrain[i];
   }

   public static GlSampler getTerrainCacheIris(int i) {
      if (WorldRenderingSettings.INSTANCE.breaksAnisotropy()) {
         i = 1;
      }

      if (terrainS[i] == null) {
         terrainS[i] = new GlSampler(((com.mojang.renderpearl.backend.opengl.GlSampler)getTerrainCache(i)).getId());
      }

      return terrainS[i];
   }
}
