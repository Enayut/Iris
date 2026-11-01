package net.irisshaders.iris.shaderpack.properties;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Iterator;
import java.util.Optional;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.helpers.OptionalBoolean;
import net.irisshaders.iris.shaderpack.parsing.DirectiveHolder;
import org.joml.Vector4f;

public class PackShadowDirectives {
   public static final int MAX_SHADOW_COLOR_BUFFERS_IRIS = 8;
   public static final int MAX_SHADOW_COLOR_BUFFERS_OF = 2;
   private final OptionalBoolean shadowEnabled;
   private final OptionalBoolean dhShadowEnabled;
   private final boolean shouldRenderTerrain;
   private final boolean shouldRenderTranslucent;
   private final boolean shouldRenderEntities;
   private final boolean shouldRenderPlayer;
   private final boolean shouldRenderBlockEntities;
   private final boolean shouldRenderLightBlockEntities;
   private final ShadowCullState cullingState;
   private final ImmutableList<PackShadowDirectives.DepthSamplingSettings> depthSamplingSettings;
   private final Int2ObjectMap<PackShadowDirectives.SamplingSettings> colorSamplingSettings;
   private int resolution;
   private Float fov;
   private float distance;
   private float nearPlane;
   private float farPlane;
   private float voxelDistance;
   private float distanceRenderMul;
   private float entityShadowDistanceMul;
   private boolean explicitRenderDistance;
   private float intervalSize;

   public PackShadowDirectives(ShaderProperties properties) {
      this.resolution = 1024;
      this.fov = null;
      this.distance = 160.0F;
      this.nearPlane = -100.05F;
      this.farPlane = 156.0F;
      this.voxelDistance = 0.0F;
      this.distanceRenderMul = -1.0F;
      this.entityShadowDistanceMul = 1.0F;
      this.explicitRenderDistance = false;
      this.intervalSize = 2.0F;
      this.shouldRenderTerrain = properties.getShadowTerrain().orElse(true);
      this.shouldRenderTranslucent = properties.getShadowTranslucent().orElse(true);
      this.shouldRenderEntities = properties.getShadowEntities().orElse(true);
      this.shouldRenderPlayer = properties.getShadowPlayer().orElse(false);
      this.shouldRenderBlockEntities = properties.getShadowBlockEntities().orElse(true);
      this.shouldRenderLightBlockEntities = properties.getShadowLightBlockEntities().orElse(false);
      this.cullingState = properties.getShadowCulling();
      this.shadowEnabled = properties.getShadowEnabled();
      this.dhShadowEnabled = properties.getDhShadowEnabled();
      this.depthSamplingSettings = ImmutableList.of(new PackShadowDirectives.DepthSamplingSettings(), new PackShadowDirectives.DepthSamplingSettings());
      Builder<PackShadowDirectives.SamplingSettings> colorSamplingSettings = ImmutableList.builder();
      this.colorSamplingSettings = new Int2ObjectArrayMap();
   }

   public PackShadowDirectives(PackShadowDirectives shadowDirectives) {
      this.resolution = shadowDirectives.resolution;
      this.fov = shadowDirectives.fov;
      this.distance = shadowDirectives.distance;
      this.nearPlane = shadowDirectives.nearPlane;
      this.farPlane = shadowDirectives.farPlane;
      this.voxelDistance = shadowDirectives.voxelDistance;
      this.distanceRenderMul = shadowDirectives.distanceRenderMul;
      this.entityShadowDistanceMul = shadowDirectives.entityShadowDistanceMul;
      this.explicitRenderDistance = shadowDirectives.explicitRenderDistance;
      this.intervalSize = shadowDirectives.intervalSize;
      this.shouldRenderTerrain = shadowDirectives.shouldRenderTerrain;
      this.shouldRenderTranslucent = shadowDirectives.shouldRenderTranslucent;
      this.shouldRenderEntities = shadowDirectives.shouldRenderEntities;
      this.shouldRenderPlayer = shadowDirectives.shouldRenderPlayer;
      this.shouldRenderBlockEntities = shadowDirectives.shouldRenderBlockEntities;
      this.shouldRenderLightBlockEntities = shadowDirectives.shouldRenderLightBlockEntities;
      this.cullingState = shadowDirectives.cullingState;
      this.depthSamplingSettings = shadowDirectives.depthSamplingSettings;
      this.colorSamplingSettings = shadowDirectives.colorSamplingSettings;
      this.shadowEnabled = shadowDirectives.shadowEnabled;
      this.dhShadowEnabled = shadowDirectives.dhShadowEnabled;
   }

   private static void acceptHardwareFilteringSettings(DirectiveHolder directives, ImmutableList<PackShadowDirectives.DepthSamplingSettings> samplers) {
      directives.acceptConstBooleanDirective("shadowHardwareFiltering", hardwareFiltering -> {
         Iterator i$ = samplers.iterator();

         while (i$.hasNext()) {
            PackShadowDirectives.DepthSamplingSettings samplerSettings = (PackShadowDirectives.DepthSamplingSettings)i$.next();
            samplerSettings.setHardwareFiltering(hardwareFiltering);
         }
      });

      for (int i = 0; i < samplers.size(); i++) {
         String name = "shadowHardwareFiltering" + i;
         directives.acceptConstBooleanDirective(name, ((PackShadowDirectives.DepthSamplingSettings)samplers.get(i))::setHardwareFiltering);
      }
   }

   private static void acceptDepthMipmapSettings(DirectiveHolder directives, ImmutableList<PackShadowDirectives.DepthSamplingSettings> samplers) {
      directives.acceptConstBooleanDirective("generateShadowMipmap", mipmap -> {
         Iterator i$ = samplers.iterator();

         while (i$.hasNext()) {
            PackShadowDirectives.SamplingSettings samplerSettings = (PackShadowDirectives.SamplingSettings)i$.next();
            samplerSettings.setMipmap(mipmap);
         }
      });
      if (!samplers.isEmpty()) {
         directives.acceptConstBooleanDirective("shadowtexMipmap", ((PackShadowDirectives.DepthSamplingSettings)samplers.getFirst())::setMipmap);
      }

      for (int i = 0; i < samplers.size(); i++) {
         String name = "shadowtex" + i + "Mipmap";
         directives.acceptConstBooleanDirective(name, ((PackShadowDirectives.DepthSamplingSettings)samplers.get(i))::setMipmap);
      }
   }

   private static void acceptColorMipmapSettings(DirectiveHolder directives, Int2ObjectMap<PackShadowDirectives.SamplingSettings> samplers) {
      directives.acceptConstBooleanDirective("generateShadowColorMipmap", mipmap -> samplers.forEach((ix, sampler) -> sampler.setMipmap(mipmap)));

      for (int i = 0; i < 8; i++) {
         String name = "shadowcolor" + i + "Mipmap";
         directives.acceptConstBooleanDirective(
            name, ((PackShadowDirectives.SamplingSettings)samplers.computeIfAbsent(i, sa -> new PackShadowDirectives.SamplingSettings()))::setMipmap
         );
         name = "shadowColor" + i + "Mipmap";
         directives.acceptConstBooleanDirective(
            name, ((PackShadowDirectives.SamplingSettings)samplers.computeIfAbsent(i, sa -> new PackShadowDirectives.SamplingSettings()))::setMipmap
         );
      }
   }

   private static void acceptDepthFilteringSettings(DirectiveHolder directives, ImmutableList<PackShadowDirectives.DepthSamplingSettings> samplers) {
      if (!samplers.isEmpty()) {
         directives.acceptConstBooleanDirective("shadowtexNearest", ((PackShadowDirectives.DepthSamplingSettings)samplers.getFirst())::setNearest);
      }

      for (int i = 0; i < samplers.size(); i++) {
         String name = "shadowtex" + i + "Nearest";
         directives.acceptConstBooleanDirective(name, ((PackShadowDirectives.DepthSamplingSettings)samplers.get(i))::setNearest);
         name = "shadow" + i + "MinMagNearest";
         directives.acceptConstBooleanDirective(name, ((PackShadowDirectives.DepthSamplingSettings)samplers.get(i))::setNearest);
      }
   }

   private static void acceptColorFilteringSettings(DirectiveHolder directives, Int2ObjectMap<PackShadowDirectives.SamplingSettings> samplers) {
      for (int i = 0; i < 8; i++) {
         String name = "shadowcolor" + i + "Nearest";
         directives.acceptConstBooleanDirective(
            name, ((PackShadowDirectives.SamplingSettings)samplers.computeIfAbsent(i, sa -> new PackShadowDirectives.SamplingSettings()))::setNearest
         );
         name = "shadowColor" + i + "Nearest";
         directives.acceptConstBooleanDirective(
            name, ((PackShadowDirectives.SamplingSettings)samplers.computeIfAbsent(i, sa -> new PackShadowDirectives.SamplingSettings()))::setNearest
         );
         name = "shadowColor" + i + "MinMagNearest";
         directives.acceptConstBooleanDirective(
            name, ((PackShadowDirectives.SamplingSettings)samplers.computeIfAbsent(i, sa -> new PackShadowDirectives.SamplingSettings()))::setNearest
         );
      }
   }

   public int getResolution() {
      return this.resolution;
   }

   public Float getFov() {
      return this.fov;
   }

   public float getDistance() {
      return this.distance;
   }

   public float getNearPlane() {
      return this.nearPlane;
   }

   public float getFarPlane() {
      return this.farPlane;
   }

   public float getVoxelDistance() {
      return this.voxelDistance;
   }

   public float getDistanceRenderMul() {
      return this.distanceRenderMul;
   }

   public float getEntityShadowDistanceMul() {
      return this.entityShadowDistanceMul;
   }

   public boolean isDistanceRenderMulExplicit() {
      return this.explicitRenderDistance;
   }

   public float getIntervalSize() {
      return this.intervalSize;
   }

   public boolean shouldRenderTerrain() {
      return this.shouldRenderTerrain;
   }

   public boolean shouldRenderTranslucent() {
      return this.shouldRenderTranslucent;
   }

   public boolean shouldRenderEntities() {
      return this.shouldRenderEntities;
   }

   public boolean shouldRenderPlayer() {
      return this.shouldRenderPlayer;
   }

   public boolean shouldRenderBlockEntities() {
      return this.shouldRenderBlockEntities;
   }

   public boolean shouldRenderLightBlockEntities() {
      return this.shouldRenderLightBlockEntities;
   }

   public ShadowCullState getCullingState() {
      return this.cullingState;
   }

   public OptionalBoolean isShadowEnabled() {
      return this.shadowEnabled;
   }

   public OptionalBoolean isDhShadowEnabled() {
      return this.dhShadowEnabled;
   }

   public ImmutableList<PackShadowDirectives.DepthSamplingSettings> getDepthSamplingSettings() {
      return this.depthSamplingSettings;
   }

   public Int2ObjectMap<PackShadowDirectives.SamplingSettings> getColorSamplingSettings() {
      return this.colorSamplingSettings;
   }

   public void acceptDirectives(DirectiveHolder directives) {
      directives.acceptCommentIntDirective("SHADOWRES", resolution -> this.resolution = resolution);
      directives.acceptConstIntDirective("shadowMapResolution", resolution -> this.resolution = resolution);
      directives.acceptCommentFloatDirective("SHADOWFOV", fov -> this.fov = fov);
      directives.acceptConstFloatDirective("shadowMapFov", fov -> this.fov = fov);
      directives.acceptCommentFloatDirective("SHADOWHPL", distance -> this.distance = distance);
      directives.acceptConstFloatDirective("shadowDistance", distance -> this.distance = distance);
      directives.acceptConstFloatDirective("shadowNearPlane", nearPlane -> this.nearPlane = nearPlane);
      directives.acceptConstFloatDirective("shadowFarPlane", farPlane -> this.farPlane = farPlane);
      directives.acceptConstFloatDirective("voxelDistance", distance -> this.voxelDistance = distance);
      directives.acceptConstFloatDirective("entityShadowDistanceMul", distance -> this.entityShadowDistanceMul = distance);
      directives.acceptConstFloatDirective("shadowDistanceRenderMul", distanceRenderMul -> {
         this.distanceRenderMul = distanceRenderMul;
         this.explicitRenderDistance = true;
      });
      directives.acceptConstFloatDirective("shadowIntervalSize", intervalSize -> this.intervalSize = intervalSize);
      acceptHardwareFilteringSettings(directives, this.depthSamplingSettings);
      acceptDepthMipmapSettings(directives, this.depthSamplingSettings);
      acceptColorMipmapSettings(directives, this.colorSamplingSettings);
      acceptDepthFilteringSettings(directives, this.depthSamplingSettings);
      acceptColorFilteringSettings(directives, this.colorSamplingSettings);
      this.acceptBufferDirectives(directives, this.colorSamplingSettings);
   }

   private void acceptBufferDirectives(DirectiveHolder directives, Int2ObjectMap<PackShadowDirectives.SamplingSettings> settings) {
      for (int i = 0; i < 8; i++) {
         String bufferName = "shadowcolor" + i;
         int finalI = i;
         directives.acceptConstStringDirective(
            bufferName + "Format",
            format -> {
               Optional<InternalTextureFormat> internalFormat = InternalTextureFormat.fromString(format);
               if (internalFormat.isPresent()) {
                  ((PackShadowDirectives.SamplingSettings)settings.computeIfAbsent(finalI, sa -> new PackShadowDirectives.SamplingSettings()))
                     .setFormat(internalFormat.get());
               } else {
                  Iris.logger.warn("Unrecognized internal texture format " + format + " specified for " + bufferName + "Format, ignoring.");
               }
            }
         );
         directives.acceptConstBooleanDirective(
            bufferName + "Clear",
            shouldClear -> ((PackShadowDirectives.SamplingSettings)settings.computeIfAbsent(finalI, sa -> new PackShadowDirectives.SamplingSettings()))
               .setClear(shouldClear)
         );
         directives.acceptConstVec4Directive(
            bufferName + "ClearColor",
            clearColor -> ((PackShadowDirectives.SamplingSettings)settings.computeIfAbsent(finalI, sa -> new PackShadowDirectives.SamplingSettings()))
               .setClearColor(clearColor)
         );
      }
   }

   @Override
   public String toString() {
      return "PackShadowDirectives{resolution="
         + this.resolution
         + ", fov="
         + this.fov
         + ", distance="
         + this.distance
         + ", distanceRenderMul="
         + this.distanceRenderMul
         + ", entityDistanceRenderMul="
         + this.entityShadowDistanceMul
         + ", intervalSize="
         + this.intervalSize
         + ", depthSamplingSettings="
         + this.depthSamplingSettings
         + ", colorSamplingSettings="
         + this.colorSamplingSettings
         + "}";
   }

   public static class DepthSamplingSettings extends PackShadowDirectives.SamplingSettings {
      private boolean hardwareFiltering = false;

      public boolean getHardwareFiltering() {
         return this.hardwareFiltering;
      }

      private void setHardwareFiltering(boolean hardwareFiltering) {
         this.hardwareFiltering = hardwareFiltering;
      }

      @Override
      public String toString() {
         return "DepthSamplingSettings{mipmap=" + this.getMipmap() + ", nearest=" + this.getNearest() + ", hardwareFiltering=" + this.hardwareFiltering + "}";
      }
   }

   public static class SamplingSettings {
      private boolean mipmap = false;
      private boolean nearest = false;
      private boolean clear = true;
      private Vector4f clearColor = new Vector4f(1.0F);
      private InternalTextureFormat format = InternalTextureFormat.RGBA;

      public boolean getMipmap() {
         return this.mipmap;
      }

      protected void setMipmap(boolean mipmap) {
         this.mipmap = mipmap;
      }

      public boolean getNearest() {
         return this.nearest || this.format.getPixelFormat().isInteger();
      }

      protected void setNearest(boolean nearest) {
         this.nearest = nearest;
      }

      public boolean getClear() {
         return this.clear;
      }

      protected void setClear(boolean clear) {
         this.clear = clear;
      }

      public Vector4f getClearColor() {
         return this.clearColor;
      }

      protected void setClearColor(Vector4f clearColor) {
         this.clearColor = clearColor;
      }

      public InternalTextureFormat getFormat() {
         return this.format;
      }

      protected void setFormat(InternalTextureFormat format) {
         this.format = format;
      }

      @Override
      public String toString() {
         return "SamplingSettings{mipmap="
            + this.mipmap
            + ", nearest="
            + this.nearest
            + ", clear="
            + this.clear
            + ", clearColor="
            + this.clearColor
            + ", format="
            + this.format.name()
            + "}";
      }
   }
}
