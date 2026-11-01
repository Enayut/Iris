package net.irisshaders.iris.shaderpack.properties;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Set;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.texture.TextureScaleOverride;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.shaderpack.parsing.DirectiveHolder;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import org.joml.Vector2i;

public class PackDirectives {
   private final PackRenderTargetDirectives renderTargetDirectives;
   private final PackShadowDirectives shadowDirectives;
   private final float drynessHalfLife;
   private int fallbackTex;
   private boolean supportsColorCorrection;
   private int noiseTextureResolution;
   private float sunPathRotation;
   private float ambientOcclusionLevel;
   private float wetnessHalfLife;
   private float eyeBrightnessHalfLife;
   private float centerDepthHalfLife;
   private CloudSetting cloudSetting;
   private CloudSetting dhCloudSetting;
   private boolean underwaterOverlay;
   private boolean vignette;
   private boolean sun;
   private boolean weather;
   private boolean weatherParticles;
   private boolean moon;
   private boolean stars;
   private boolean sky;
   private boolean rainDepth;
   private boolean separateAo;
   private boolean breaksAnisotropy;
   private boolean voxelizeLightBlocks;
   private boolean separateEntityDraws;
   private boolean skipAllRendering;
   private boolean frustumCulling;
   private boolean supportsEndFlash;
   private boolean occlusionCulling;
   private boolean oldLighting;
   private boolean concurrentCompute;
   private boolean oldHandLight;
   private boolean prepareBeforeShadow;
   private Object2ObjectMap<String, Object2BooleanMap<String>> explicitFlips = new Object2ObjectOpenHashMap();
   private Object2ObjectMap<String, TextureScaleOverride> scaleOverrides = new Object2ObjectOpenHashMap();
   private Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap;
   private ParticleRenderingSettings particleRenderingSettings;

   private PackDirectives(Set<Integer> supportedRenderTargets, PackShadowDirectives packShadowDirectives) {
      this.noiseTextureResolution = 256;
      this.sunPathRotation = 0.0F;
      this.supportsColorCorrection = false;
      this.ambientOcclusionLevel = 1.0F;
      this.wetnessHalfLife = 600.0F;
      this.drynessHalfLife = 200.0F;
      this.eyeBrightnessHalfLife = 10.0F;
      this.centerDepthHalfLife = 1.0F;
      this.renderTargetDirectives = new PackRenderTargetDirectives(supportedRenderTargets);
      this.shadowDirectives = packShadowDirectives;
   }

   public PackDirectives(Set<Integer> supportedRenderTargets, ShaderProperties properties) {
      this(supportedRenderTargets, new PackShadowDirectives(properties));
      this.cloudSetting = properties.getCloudSetting();
      this.dhCloudSetting = properties.getDHCloudSetting();
      this.underwaterOverlay = properties.getUnderwaterOverlay().orElse(false);
      this.vignette = properties.getVignette().orElse(false);
      this.sun = properties.getSun().orElse(true);
      this.weather = properties.getWeather().orElse(true);
      this.weatherParticles = properties.getWeatherParticles().orElse(true);
      this.moon = properties.getMoon().orElse(true);
      this.stars = properties.getStars().orElse(true);
      this.sky = properties.getSky().orElse(true);
      this.rainDepth = properties.getRainDepth().orElse(false);
      this.separateAo = properties.getSeparateAo().orElse(false);
      this.breaksAnisotropy = properties.breaksAnisotropy().orElse(false);
      this.voxelizeLightBlocks = properties.getVoxelizeLightBlocks().orElse(false);
      this.separateEntityDraws = properties.getSeparateEntityDraws().orElse(false);
      this.skipAllRendering = properties.skipAllRendering().orElse(false);
      this.frustumCulling = properties.getFrustumCulling().orElse(true);
      this.supportsEndFlash = properties.supportsEndFlash().orElse(false);
      this.occlusionCulling = properties.getOcclusionCulling().orElse(true);
      this.oldLighting = properties.getOldLighting().orElse(false);
      this.fallbackTex = properties.getFallbackTex();
      this.supportsColorCorrection = properties.supportsColorCorrection().orElse(false);
      this.concurrentCompute = properties.getConcurrentCompute().orElse(false);
      this.oldHandLight = properties.getOldHandLight().orElse(true);
      this.explicitFlips = properties.getExplicitFlips();
      this.scaleOverrides = properties.getTextureScaleOverrides();
      this.prepareBeforeShadow = properties.getPrepareBeforeShadow().orElse(false);
      this.particleRenderingSettings = properties.getParticleRenderingSettings();
      this.textureMap = properties.getCustomTexturePatching();
   }

   PackDirectives(Set<Integer> supportedRenderTargets, PackDirectives directives) {
      this(supportedRenderTargets, new PackShadowDirectives(directives.getShadowDirectives()));
      this.cloudSetting = directives.cloudSetting;
      this.dhCloudSetting = directives.dhCloudSetting;
      this.separateAo = directives.separateAo;
      this.voxelizeLightBlocks = directives.voxelizeLightBlocks;
      this.separateEntityDraws = directives.separateEntityDraws;
      this.frustumCulling = directives.frustumCulling;
      this.supportsEndFlash = directives.supportsEndFlash;
      this.oldLighting = directives.oldLighting;
      this.concurrentCompute = directives.concurrentCompute;
      this.explicitFlips = directives.explicitFlips;
      this.scaleOverrides = directives.scaleOverrides;
      this.prepareBeforeShadow = directives.prepareBeforeShadow;
      this.fallbackTex = directives.fallbackTex;
      this.particleRenderingSettings = directives.particleRenderingSettings;
      this.textureMap = directives.textureMap;
   }

   private static float clamp(float val, float lo, float hi) {
      return Math.max(lo, Math.min(hi, val));
   }

   public int getNoiseTextureResolution() {
      return this.noiseTextureResolution;
   }

   public float getSunPathRotation() {
      return this.sunPathRotation;
   }

   public float getAmbientOcclusionLevel() {
      return this.ambientOcclusionLevel;
   }

   public float getWetnessHalfLife() {
      return this.wetnessHalfLife;
   }

   public float getDrynessHalfLife() {
      return this.drynessHalfLife;
   }

   public float getEyeBrightnessHalfLife() {
      return this.eyeBrightnessHalfLife;
   }

   public float getCenterDepthHalfLife() {
      return this.centerDepthHalfLife;
   }

   public CloudSetting getCloudSetting() {
      return this.cloudSetting;
   }

   public CloudSetting getDHCloudSetting() {
      return this.dhCloudSetting;
   }

   public boolean underwaterOverlay() {
      return this.underwaterOverlay;
   }

   public boolean vignette() {
      return this.vignette;
   }

   public boolean shouldRenderSun() {
      return this.sun;
   }

   public boolean shouldRenderWeather() {
      return this.weather;
   }

   public boolean shouldRenderWeatherParticles() {
      return this.weatherParticles;
   }

   public boolean shouldRenderMoon() {
      return this.moon;
   }

   public boolean shouldRenderStars() {
      return this.stars;
   }

   public boolean shouldRenderSkyDisc() {
      return this.sky;
   }

   public ParticleRenderingSettings getParticleRenderingSettings() {
      return this.particleRenderingSettings;
   }

   public boolean rainDepth() {
      return this.rainDepth;
   }

   public boolean shouldUseSeparateAo() {
      return this.separateAo;
   }

   public boolean breaksAnisotropy() {
      return this.breaksAnisotropy;
   }

   public boolean shouldVoxelizeLightBlocks() {
      return this.voxelizeLightBlocks;
   }

   public boolean shouldUseSeparateEntityDraws() {
      return this.separateEntityDraws;
   }

   public boolean shouldUseFrustumCulling() {
      return this.frustumCulling;
   }

   public boolean shouldUseOcclusionCulling() {
      return this.occlusionCulling;
   }

   public boolean isOldLighting() {
      return this.oldLighting;
   }

   public boolean isOldHandLight() {
      return this.oldHandLight;
   }

   public boolean getConcurrentCompute() {
      return this.concurrentCompute;
   }

   public boolean isPrepareBeforeShadow() {
      return this.prepareBeforeShadow;
   }

   public boolean skipAllRendering() {
      return this.skipAllRendering;
   }

   public Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> getTextureMap() {
      return this.textureMap;
   }

   public PackRenderTargetDirectives getRenderTargetDirectives() {
      return this.renderTargetDirectives;
   }

   public PackShadowDirectives getShadowDirectives() {
      return this.shadowDirectives;
   }

   public boolean supportsColorCorrection() {
      return this.supportsColorCorrection;
   }

   public int getFallbackTex() {
      return this.fallbackTex;
   }

   public void acceptDirectivesFrom(DirectiveHolder directives) {
      this.renderTargetDirectives.acceptDirectives(directives);
      this.shadowDirectives.acceptDirectives(directives);
      directives.acceptConstIntDirective("noiseTextureResolution", noiseTextureResolution -> this.noiseTextureResolution = noiseTextureResolution);
      directives.acceptConstFloatDirective("sunPathRotation", sunPathRotation -> this.sunPathRotation = sunPathRotation);
      directives.acceptConstFloatDirective(
         "ambientOcclusionLevel", ambientOcclusionLevel -> this.ambientOcclusionLevel = clamp(ambientOcclusionLevel, 0.0F, 1.0F)
      );
      directives.acceptConstFloatDirective("wetnessHalflife", wetnessHalfLife -> this.wetnessHalfLife = wetnessHalfLife);
      directives.acceptConstFloatDirective("drynessHalflife", wetnessHalfLife -> this.wetnessHalfLife = wetnessHalfLife);
      directives.acceptConstFloatDirective("eyeBrightnessHalflife", eyeBrightnessHalfLife -> this.eyeBrightnessHalfLife = eyeBrightnessHalfLife);
      directives.acceptConstFloatDirective("centerDepthHalflife", centerDepthHalfLife -> this.centerDepthHalfLife = centerDepthHalfLife);
   }

   public ImmutableMap<Integer, Boolean> getExplicitFlips(String pass) {
      Builder<Integer, Boolean> explicitFlips = ImmutableMap.builder();
      Object2BooleanMap<String> explicitFlipsStr = (Object2BooleanMap<String>)this.explicitFlips.get(pass);
      if (explicitFlipsStr == null) {
         explicitFlipsStr = Object2BooleanMaps.emptyMap();
      }

      explicitFlipsStr.forEach((buffer, shouldFlip) -> {
         int index = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.indexOf(buffer);
         if (index == -1 && buffer.startsWith("colortex")) {
            String id = buffer.substring("colortex".length());

            try {
               index = Integer.parseInt(id);
            } catch (NumberFormatException var7) {
            }
         }

         if (index != -1) {
            explicitFlips.put(index, shouldFlip);
         } else {
            Iris.logger.warn("Unknown buffer with ID " + buffer + " specified in flip directive for pass " + pass);
         }
      });
      return explicitFlips.build();
   }

   public Vector2i getTextureScaleOverride(int index, int dimensionX, int dimensionY) {
      String name = "colortex" + index;
      Vector2i scale = new Vector2i();
      if (index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
         String legacyName = (String)PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index);
         if (this.scaleOverrides.containsKey(legacyName)) {
            scale.set(
               ((TextureScaleOverride)this.scaleOverrides.get(legacyName)).getX(dimensionX),
               ((TextureScaleOverride)this.scaleOverrides.get(legacyName)).getY(dimensionY)
            );
         } else if (this.scaleOverrides.containsKey(name)) {
            scale.set(
               ((TextureScaleOverride)this.scaleOverrides.get(name)).getX(dimensionX), ((TextureScaleOverride)this.scaleOverrides.get(name)).getY(dimensionY)
            );
         } else {
            scale.set(dimensionX, dimensionY);
         }
      } else if (this.scaleOverrides.containsKey(name)) {
         scale.set(
            ((TextureScaleOverride)this.scaleOverrides.get(name)).getX(dimensionX), ((TextureScaleOverride)this.scaleOverrides.get(name)).getY(dimensionY)
         );
      } else {
         scale.set(dimensionX, dimensionY);
      }

      return scale;
   }

   public boolean supportsEndFlash() {
      return this.supportsEndFlash;
   }
}
