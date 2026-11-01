package net.irisshaders.iris.pipeline;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.OptionalInt;
import java.util.Set;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.shaderpack.properties.CloudSetting;
import net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

public interface WorldRenderingPipeline {
   void beginLevelRendering();

   void renderShadows(LevelRendererAccessor var1, Camera var2, CameraRenderState var3);

   void addDebugText(DebugScreenDisplayer var1);

   OptionalInt getForcedShadowRenderDistanceChunksForDisplay();

   Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> getTextureMap();

   default Set<String> getTextureOverrides(TextureStage stage) {
      return Set.of();
   }

   WorldRenderingPhase getPhase();

   void setPhase(WorldRenderingPhase var1);

   void setOverridePhase(WorldRenderingPhase var1);

   int getCurrentNormalTexture();

   int getCurrentSpecularTexture();

   void onSetAlbedoTex(GpuTextureView var1);

   void beginHand();

   void beginTranslucents();

   void finalizeLevelRendering();

   void finalizeGameRendering();

   void destroy();

   FrameUpdateNotifier getFrameUpdateNotifier();

   boolean shouldDisableVanillaEntityShadows();

   boolean shouldDisableDirectionalShading();

   boolean shouldDisableFrustumCulling();

   boolean shouldDisableOcclusionCulling();

   CloudSetting getCloudSetting();

   boolean shouldRenderUnderwaterOverlay();

   boolean shouldRenderVignette();

   boolean shouldRenderSun();

   boolean shouldRenderWeather();

   boolean shouldRenderWeatherParticles();

   boolean shouldRenderMoon();

   boolean shouldRenderStars();

   boolean shouldRenderSkyDisc();

   boolean shouldWriteRainAndSnowToDepthBuffer();

   ParticleRenderingSettings getParticleRenderingSettings();

   boolean allowConcurrentCompute();

   boolean hasFeature(FeatureFlags var1);

   float getSunPathRotation();

   DHCompat getDHCompat();

   void setIsMainBound(boolean var1);

   void onBeginClear();

   boolean supportsEndFlash();

   int getAlbedoTex();
}
