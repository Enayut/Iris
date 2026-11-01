package net.irisshaders.iris.pipeline;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.backend.opengl.GlConst;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.image.ImageClearPass;
import net.irisshaders.iris.gl.image.ImageHolder;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.program.ProgramImages;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.helpers.FakeChainedJsonException;
import net.irisshaders.iris.helpers.OptionalBoolean;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.layer.GbufferPrograms;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.pathways.CenterDepthSampler;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pathways.HorizonRenderer;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import net.irisshaders.iris.pathways.colorspace.ColorSpaceConverter;
import net.irisshaders.iris.pathways.colorspace.ColorSpaceFragmentConverter;
import net.irisshaders.iris.pbr.format.TextureFormat;
import net.irisshaders.iris.pbr.format.TextureFormatLoader;
import net.irisshaders.iris.pbr.texture.PBRTextureHolder;
import net.irisshaders.iris.pbr.texture.PBRTextureManager;
import net.irisshaders.iris.pbr.texture.PBRType;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.pipeline.programs.ShaderCreator;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.pipeline.programs.ShaderLoadingMap;
import net.irisshaders.iris.pipeline.programs.ShaderMap;
import net.irisshaders.iris.pipeline.programs.ShaderSupplier;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.ShaderPrinter;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.samplers.IrisImages;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.FilledIndirectPointer;
import net.irisshaders.iris.shaderpack.ImageInformation;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.loading.ProgramArrayId;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.materialmap.BlockMaterialMapping;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.programs.ComputeSource;
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.CloudSetting;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackShadowDirectives;
import net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.shadows.ShadowCompositeRenderer;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.targets.Blaze3dRenderTargetExt;
import net.irisshaders.iris.targets.BufferFlipper;
import net.irisshaders.iris.targets.ClearPass;
import net.irisshaders.iris.targets.ClearPassCreator;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.targets.backed.NativeImageBackedSingleColorTexture;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.vertices.sodium.terrain.FormatAnalyzer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector4f;

public class IrisRenderingPipeline implements WorldRenderingPipeline, ShaderRenderingPipeline {
   private final RenderTargets renderTargets;
   private final ShaderMap shaderMap;
   private final CustomUniforms customUniforms;
   private final ShadowCompositeRenderer shadowCompositeRenderer;
   private final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> customTextureMap;
   private final ComputeProgram[] setup;
   private final boolean separateHardwareSamplers;
   private final ProgramFallbackResolver resolver;
   private final Supplier<ShadowRenderTargets> shadowTargetsSupplier;
   private final Set<GlProgram> loadedShaders;
   private final CompositeRenderer beginRenderer;
   private final CompositeRenderer prepareRenderer;
   private final CompositeRenderer deferredRenderer;
   private final CompositeRenderer compositeRenderer;
   private final FinalPassRenderer finalPassRenderer;
   private final CustomTextureManager customTextureManager;
   private final DynamicTexture whitePixel;
   private final DynamicTexture biggerWhitePixel;
   private final FrameUpdateNotifier updateNotifier;
   private final CenterDepthSampler centerDepthSampler;
   private final ColorSpaceConverter colorSpaceConverter;
   private final ImmutableSet<Integer> flippedBeforeShadow;
   private final ImmutableSet<Integer> flippedAfterPrepare;
   private final ImmutableSet<Integer> flippedAfterTranslucent;
   private final HorizonRenderer horizonRenderer = new HorizonRenderer();
   @Nullable
   private final ComputeProgram[] shadowComputes;
   private final float sunPathRotation;
   private final boolean shouldRenderUnderwaterOverlay;
   private final boolean shouldRenderVignette;
   private final boolean shouldWriteRainAndSnowToDepthBuffer;
   private final boolean oldLighting;
   private final OptionalInt forcedShadowRenderDistanceChunks;
   private final boolean frustumCulling;
   private final boolean occlusionCulling;
   private final CloudSetting cloudSetting;
   private final boolean shouldRenderSun;
   private final boolean shouldRenderWeather;
   private final boolean shouldRenderWeatherParticles;
   private final boolean shouldRenderMoon;
   private final boolean shouldRenderStars;
   private final boolean shouldRenderSkyDisc;
   private final boolean allowConcurrentCompute;
   @Nullable
   private final ShadowRenderer shadowRenderer;
   private final int shadowMapResolution;
   private final ParticleRenderingSettings particleRenderingSettings;
   private final PackDirectives packDirectives;
   private final Set<GlImage> customImages;
   private final ImmutableList<ImageClearPass> clearImages;
   private final ShaderPack pack;
   private final PackShadowDirectives shadowDirectives;
   private final DHCompat dhCompat;
   private final int stackSize = 0;
   private final boolean skipAllRendering;
   private final CloudSetting dhCloudSetting;
   public boolean isBeforeTranslucent;
   private boolean initializedBlockIds;
   private ShaderStorageBufferHolder shaderStorageBufferHolder;
   private ShadowRenderTargets shadowRenderTargets;
   private WorldRenderingPhase overridePhase = null;
   private WorldRenderingPhase phase = WorldRenderingPhase.NONE;
   private ImmutableList<ClearPass> clearPassesFull;
   private ImmutableList<ClearPass> clearPasses;
   private ImmutableList<ClearPass> shadowClearPasses;
   private ImmutableList<ClearPass> shadowClearPassesFull;
   private boolean destroyed = false;
   private boolean isRenderingWorld;
   private boolean isMainBound;
   private boolean shouldBindPBR;
   private AbstractTexture currentNormalTexture;
   private AbstractTexture currentSpecularTexture;
   private ColorSpace currentColorSpace;
   private GlFramebuffer defaultFB;
   private GlFramebuffer defaultFBAlt;
   private GlFramebuffer defaultFBShadow;
   private final boolean supportsEndFlash;
   private GlSampler normalSampler = GlSampler.MIPPED_NEAREST;
   private GlSampler specularSampler = GlSampler.MIPPED_NEAREST;
   private int albedoTex;
   private boolean shouldRemovePhase = false;

   public IrisRenderingPipeline(ProgramSet programSet) {
      ShaderPrinter.resetPrintState();
      net.irisshaders.iris.vulkan.IrisVulkanBackend.detect();
      if (net.irisshaders.iris.vulkan.IrisVulkanBackend.isChainActive()) {
         net.irisshaders.iris.vulkan.IrisVulkanRenderer.get().initialize(
            Minecraft.getInstance().gameRenderer.mainRenderTarget().width,
            Minecraft.getInstance().gameRenderer.mainRenderTarget().height
         );
      }
      this.shouldRenderUnderwaterOverlay = programSet.getPackDirectives().underwaterOverlay();
      this.supportsEndFlash = programSet.getPackDirectives().supportsEndFlash();
      this.shouldRenderVignette = programSet.getPackDirectives().vignette();
      this.shouldWriteRainAndSnowToDepthBuffer = programSet.getPackDirectives().rainDepth();
      this.oldLighting = programSet.getPackDirectives().isOldLighting();
      this.updateNotifier = new FrameUpdateNotifier();
      this.packDirectives = programSet.getPackDirectives();
      this.customTextureMap = programSet.getPackDirectives().getTextureMap();
      this.separateHardwareSamplers = programSet.getPack().hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS);
      this.shadowDirectives = this.packDirectives.getShadowDirectives();
      this.cloudSetting = programSet.getPackDirectives().getCloudSetting();
      this.dhCloudSetting = programSet.getPackDirectives().getDHCloudSetting();
      this.shouldRenderSun = programSet.getPackDirectives().shouldRenderSun();
      this.shouldRenderWeather = programSet.getPackDirectives().shouldRenderWeather();
      this.shouldRenderWeatherParticles = programSet.getPackDirectives().shouldRenderWeatherParticles();
      this.shouldRenderMoon = programSet.getPackDirectives().shouldRenderMoon();
      this.shouldRenderStars = programSet.getPackDirectives().shouldRenderStars();
      this.shouldRenderSkyDisc = programSet.getPackDirectives().shouldRenderSkyDisc();
      this.allowConcurrentCompute = programSet.getPackDirectives().getConcurrentCompute();
      this.skipAllRendering = programSet.getPackDirectives().skipAllRendering();
      this.frustumCulling = programSet.getPackDirectives().shouldUseFrustumCulling();
      this.occlusionCulling = programSet.getPackDirectives().shouldUseOcclusionCulling();
      this.resolver = new ProgramFallbackResolver(programSet);
      this.pack = programSet.getPack();
      WorldRenderingSettings.INSTANCE.setVertexFormat(FormatAnalyzer.createFormat(true, true, true, true));
      RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      GpuTexture depthTexture = main.getDepthTexture();
      int internalFormat = GlConst.toGlInternalId(depthTexture.getFormat());
      DepthBufferFormat depthBufferFormat = DepthBufferFormat.fromGlEnumOrDefault(internalFormat);
      if (!this.pack.getBufferObjects().isEmpty()) {
         if (!IrisRenderSystem.supportsSSBO()) {
            throw new IllegalStateException(
               "Shader storage buffers/immutable buffer storage is not supported on this graphics card, however the shaderpack requested them? This shouldn't be possible."
            );
         }

         this.shaderStorageBufferHolder = new ShaderStorageBufferHolder(this.pack.getBufferObjects(), main.width, main.height);
         this.shaderStorageBufferHolder.setupBuffers();
      } else {
         for (int i = 0; i < Math.min(16, SamplerLimits.get().getMaxShaderStorageUnits()); i++) {
            IrisRenderSystem.bindBufferBase(37074, i, 0);
         }
      }

      this.customImages = new HashSet<>();

      for (ImageInformation information : programSet.getPack().getIrisCustomImages()) {
         if (information.isRelative()) {
            this.customImages
               .add(
                  new GlImage.Relative(
                     information.name(),
                     information.samplerName(),
                     information.format(),
                     information.internalTextureFormat(),
                     information.type(),
                     information.clear(),
                     information.relativeWidth(),
                     information.relativeHeight(),
                     main.width,
                     main.height
                  )
               );
         } else {
            this.customImages
               .add(
                  new GlImage(
                     information.name(),
                     information.samplerName(),
                     information.target(),
                     information.format(),
                     information.internalTextureFormat(),
                     information.type(),
                     information.clear(),
                     information.width(),
                     information.height(),
                     information.depth()
                  )
               );
         }
      }

      this.clearImages = this.customImages.stream().filter(GlImage::shouldClear).map(ImageClearPass::create).collect(ImmutableList.toImmutableList());
      if (programSet.getPackDirectives().getParticleRenderingSettings() != ParticleRenderingSettings.UNSET) {
         this.particleRenderingSettings = programSet.getPackDirectives().getParticleRenderingSettings();
      } else if (programSet.getComposite(ProgramArrayId.Deferred).length > 0 && !programSet.getPackDirectives().shouldUseSeparateEntityDraws()) {
         this.particleRenderingSettings = ParticleRenderingSettings.AFTER;
      } else {
         this.particleRenderingSettings = ParticleRenderingSettings.MIXED;
      }

      this.renderTargets = new RenderTargets(
         main.width,
         main.height,
         depthTexture,
         ((Blaze3dRenderTargetExt)main).iris$getDepthBufferVersion(),
         depthBufferFormat,
         programSet.getPackDirectives().getRenderTargetDirectives().getRenderTargetSettings(),
         programSet.getPackDirectives()
      );
      this.sunPathRotation = programSet.getPackDirectives().getSunPathRotation();
      PackShadowDirectives shadowDirectives = programSet.getPackDirectives().getShadowDirectives();
      if (shadowDirectives.isDistanceRenderMulExplicit()) {
         if (shadowDirectives.getDistanceRenderMul() >= 0.0) {
            this.forcedShadowRenderDistanceChunks = OptionalInt.of(((int)(shadowDirectives.getDistance() * shadowDirectives.getDistanceRenderMul()) + 15) / 16);
         } else {
            this.forcedShadowRenderDistanceChunks = OptionalInt.of(-1);
         }
      } else {
         this.forcedShadowRenderDistanceChunks = OptionalInt.empty();
      }

      this.customUniforms = programSet.getPack()
         .customUniforms
         .build(holder -> CommonUniforms.addNonDynamicUniforms(holder, programSet.getPack().getIdMap(), programSet.getPackDirectives(), this.updateNotifier));
      GlStateManager._activeTexture(33986);
      this.customTextureManager = new CustomTextureManager(
         programSet.getPackDirectives(),
         programSet.getPack().getCustomTextureDataMap(),
         programSet.getPack().getIrisCustomTextureDataMap(),
         programSet.getPack().getCustomNoiseTexture()
      );
      this.whitePixel = new NativeImageBackedSingleColorTexture(255, 255, 255, 255);
      this.biggerWhitePixel = new NativeImageBackedSingleColorTexture(32, 32, 255, 255, 255, 255);
      GlStateManager._activeTexture(33984);
      BufferFlipper flipper = new BufferFlipper();
      this.centerDepthSampler = new CenterDepthSampler(
         () -> this.renderTargets.getDepthTexture().iris$getGlId(), programSet.getPackDirectives().getCenterDepthHalfLife()
      );
      this.shadowMapResolution = programSet.getPackDirectives().getShadowDirectives().getResolution();
      this.shadowTargetsSupplier = () -> {
         if (this.shadowRenderTargets == null) {
            this.shadowRenderTargets = new ShadowRenderTargets(this, this.shadowMapResolution, shadowDirectives);
         }

         return this.shadowRenderTargets;
      };
      if (shadowDirectives.isShadowEnabled() == OptionalBoolean.TRUE) {
         this.shadowTargetsSupplier.get();
      }

      this.shadowComputes = this.createShadowComputes(programSet.getShadowCompute(), programSet);
      if (FullScreenQuadRenderer.init() != -1) {
         throw new IllegalStateException("WHY");
      }

      this.beginRenderer = new CompositeRenderer(
         this,
         CompositePass.BEGIN,
         programSet.getPackDirectives(),
         programSet.getComposite(ProgramArrayId.Begin),
         programSet.getCompute(ProgramArrayId.Begin),
         this.renderTargets,
         this.shaderStorageBufferHolder,
         this.customTextureManager.getNoiseTexture(),
         this.updateNotifier,
         this.centerDepthSampler,
         flipper,
         this.shadowTargetsSupplier,
         TextureStage.BEGIN,
         this.customTextureManager.getCustomTextureIdMap().getOrDefault(TextureStage.BEGIN, Object2ObjectMaps.emptyMap()),
         this.customTextureManager.getIrisCustomTextures(),
         this.customImages,
         programSet.getPackDirectives().getExplicitFlips("begin_pre"),
         this.customUniforms
      );
      this.flippedBeforeShadow = flipper.snapshot();
      this.prepareRenderer = new CompositeRenderer(
         this,
         CompositePass.PREPARE,
         programSet.getPackDirectives(),
         programSet.getComposite(ProgramArrayId.Prepare),
         programSet.getCompute(ProgramArrayId.Prepare),
         this.renderTargets,
         this.shaderStorageBufferHolder,
         this.customTextureManager.getNoiseTexture(),
         this.updateNotifier,
         this.centerDepthSampler,
         flipper,
         this.shadowTargetsSupplier,
         TextureStage.PREPARE,
         this.customTextureManager.getCustomTextureIdMap().getOrDefault(TextureStage.PREPARE, Object2ObjectMaps.emptyMap()),
         this.customTextureManager.getIrisCustomTextures(),
         this.customImages,
         programSet.getPackDirectives().getExplicitFlips("prepare_pre"),
         this.customUniforms
      );
      this.flippedAfterPrepare = flipper.snapshot();
      this.deferredRenderer = new CompositeRenderer(
         this,
         CompositePass.DEFERRED,
         programSet.getPackDirectives(),
         programSet.getComposite(ProgramArrayId.Deferred),
         programSet.getCompute(ProgramArrayId.Deferred),
         this.renderTargets,
         this.shaderStorageBufferHolder,
         this.customTextureManager.getNoiseTexture(),
         this.updateNotifier,
         this.centerDepthSampler,
         flipper,
         this.shadowTargetsSupplier,
         TextureStage.DEFERRED,
         this.customTextureManager.getCustomTextureIdMap().getOrDefault(TextureStage.DEFERRED, Object2ObjectMaps.emptyMap()),
         this.customTextureManager.getIrisCustomTextures(),
         this.customImages,
         programSet.getPackDirectives().getExplicitFlips("deferred_pre"),
         this.customUniforms
      );
      this.flippedAfterTranslucent = flipper.snapshot();
      this.compositeRenderer = new CompositeRenderer(
         this,
         CompositePass.COMPOSITE,
         programSet.getPackDirectives(),
         programSet.getComposite(ProgramArrayId.Composite),
         programSet.getCompute(ProgramArrayId.Composite),
         this.renderTargets,
         this.shaderStorageBufferHolder,
         this.customTextureManager.getNoiseTexture(),
         this.updateNotifier,
         this.centerDepthSampler,
         flipper,
         this.shadowTargetsSupplier,
         TextureStage.COMPOSITE_AND_FINAL,
         this.customTextureManager.getCustomTextureIdMap().getOrDefault(TextureStage.COMPOSITE_AND_FINAL, Object2ObjectMaps.emptyMap()),
         this.customTextureManager.getIrisCustomTextures(),
         this.customImages,
         programSet.getPackDirectives().getExplicitFlips("composite_pre"),
         this.customUniforms
      );
      this.finalPassRenderer = new FinalPassRenderer(
         this,
         programSet,
         this.renderTargets,
         this.customTextureManager.getNoiseTexture(),
         this.shaderStorageBufferHolder,
         this.updateNotifier,
         flipper.snapshot(),
         this.centerDepthSampler,
         this.shadowTargetsSupplier,
         this.customTextureManager.getCustomTextureIdMap().getOrDefault(TextureStage.COMPOSITE_AND_FINAL, Object2ObjectMaps.emptyMap()),
         this.customTextureManager.getIrisCustomTextures(),
         this.customImages,
         this.compositeRenderer.getFlippedAtLeastOnceFinal(),
         this.customUniforms
      );
      if (net.irisshaders.iris.vulkan.IrisVulkanBackend.isChainActive()) {
         net.irisshaders.iris.vulkan.IrisVulkanRenderer.get().compilePasses(
            net.irisshaders.iris.pipeline.CompositePass.DEFERRED,
            programSet.getComposite(net.irisshaders.iris.shaderpack.loading.ProgramArrayId.Deferred),
            net.irisshaders.iris.shaderpack.texture.TextureStage.DEFERRED,
            this.customTextureMap,
            this.getTextureOverrides(net.irisshaders.iris.shaderpack.texture.TextureStage.DEFERRED)
         );
         net.irisshaders.iris.vulkan.IrisVulkanRenderer.get().compilePasses(
            net.irisshaders.iris.pipeline.CompositePass.COMPOSITE,
            programSet.getComposite(net.irisshaders.iris.shaderpack.loading.ProgramArrayId.Composite),
            net.irisshaders.iris.shaderpack.texture.TextureStage.COMPOSITE_AND_FINAL,
            this.customTextureMap,
            this.getTextureOverrides(net.irisshaders.iris.shaderpack.texture.TextureStage.COMPOSITE_AND_FINAL)
         );
      }

      Supplier<ImmutableSet<Integer>> flipped = () -> this.isBeforeTranslucent ? this.flippedAfterPrepare : this.flippedAfterTranslucent;
      IntFunction<ProgramSamplers> createTerrainSamplers = programId -> {
         ProgramSamplers.Builder builder = ProgramSamplers.builder(programId, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
         ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(
            builder, this.customTextureManager.getCustomTextureIdMap().getOrDefault(TextureStage.GBUFFERS_AND_SHADOW, Object2ObjectMaps.emptyMap())
         );
         IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, this.renderTargets, false, this);
         IrisSamplers.addCustomTextures(builder, this.customTextureManager.getIrisCustomTextures());
         if (!this.shouldBindPBR) {
            this.shouldBindPBR = IrisSamplers.hasPBRSamplers(customTextureSamplerInterceptor);
         }

         IrisSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, this.whitePixel, true, true, false);
         IrisSamplers.addWorldDepthSamplers(customTextureSamplerInterceptor, this.renderTargets);
         IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, this.customTextureManager.getNoiseTexture());
         IrisSamplers.addCustomImages(customTextureSamplerInterceptor, this.customImages);
         if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
            IrisSamplers.addShadowSamplers(
               customTextureSamplerInterceptor, Objects.requireNonNull(this.shadowRenderTargets), null, this.separateHardwareSamplers
            );
         }

         return builder.build();
      };
      IntFunction<ProgramImages> createTerrainImages = programId -> {
         ProgramImages.Builder builder = ProgramImages.builder(programId);
         IrisImages.addRenderTargetImages(builder, flipped, this.renderTargets);
         IrisImages.addCustomImages(builder, this.customImages);
         if (IrisImages.hasShadowImages(builder)) {
            IrisImages.addShadowColorImages(builder, Objects.requireNonNull(this.shadowRenderTargets), null);
         }

         return builder.build();
      };
      this.dhCompat = new DHCompat(this, shadowDirectives.isDhShadowEnabled().orElse(true));
      this.loadedShaders = new HashSet<>();
      ShaderLoadingMap loadingMap = new ShaderLoadingMap(
         (key, patchType) -> {
            try {
               return key.isShadow()
                  ? this.createShadowShader(key.getName(), this.resolver.resolve(key.getProgram()), key, patchType)
                  : this.createShader(key.getName(), this.resolver.resolve(key.getProgram()), key, patchType);
            } catch (FakeChainedJsonException e) {
               this.destroyShaders();
               throw e.getTrueException();
            } catch (IOException e) {
               this.destroyShaders();
               throw new RuntimeException(e);
            } catch (RuntimeException e) {
               this.destroyShaders();
               throw e;
            }
         }
      );
      this.shaderMap = new ShaderMap(loadingMap, shader -> shader.key().isShadow() ? this.shadowRenderTargets == null : false, this.loadedShaders::add);
      this.initializedBlockIds = false;
      WorldRenderingSettings.INSTANCE.setEntityIds(programSet.getPack().getIdMap().getEntityIdMap());
      WorldRenderingSettings.INSTANCE.setItemIds(programSet.getPack().getIdMap().getItemIdMap());
      WorldRenderingSettings.INSTANCE.setAmbientOcclusionLevel(programSet.getPackDirectives().getAmbientOcclusionLevel());
      WorldRenderingSettings.INSTANCE.setDisableDirectionalShading(this.shouldDisableDirectionalShading());
      WorldRenderingSettings.INSTANCE.setUseSeparateAo(programSet.getPackDirectives().shouldUseSeparateAo());
      WorldRenderingSettings.INSTANCE.setBreaksAnisotropy(programSet.getPackDirectives().breaksAnisotropy());
      WorldRenderingSettings.INSTANCE.setVoxelizeLightBlocks(programSet.getPackDirectives().shouldVoxelizeLightBlocks());
      WorldRenderingSettings.INSTANCE.setSeparateEntityDraws(programSet.getPackDirectives().shouldUseSeparateEntityDraws());
      if (this.shadowRenderTargets != null) {
         GlProgram shader = this.shaderMap.getShader(ShaderKey.SHADOW_TERRAIN_CUTOUT);
         boolean shadowUsesImages = false;
         if (shader instanceof ExtendedShader shader2) {
            shadowUsesImages = shader2.hasActiveImages();
         }

         this.shadowClearPasses = ClearPassCreator.createShadowClearPasses(this.shadowRenderTargets, false, shadowDirectives);
         this.shadowClearPassesFull = ClearPassCreator.createShadowClearPasses(this.shadowRenderTargets, true, shadowDirectives);
         this.shadowCompositeRenderer = new ShadowCompositeRenderer(
            this,
            programSet.getPackDirectives(),
            programSet.getComposite(ProgramArrayId.ShadowComposite),
            programSet.getCompute(ProgramArrayId.ShadowComposite),
            this.shadowRenderTargets,
            this.shaderStorageBufferHolder,
            this.customTextureManager.getNoiseTexture(),
            this.updateNotifier,
            this.customTextureManager.getCustomTextureIdMap(TextureStage.SHADOWCOMP),
            this.customImages,
            programSet.getPackDirectives().getExplicitFlips("shadowcomp_pre"),
            this.customTextureManager.getIrisCustomTextures(),
            this.customUniforms
         );
         if (programSet.getPackDirectives().getShadowDirectives().isShadowEnabled().orElse(true)) {
            this.shadowRenderer = new ShadowRenderer(
               this,
               this.resolver.resolveNullable(ProgramId.ShadowSolid),
               programSet.getPackDirectives(),
               this.shadowRenderTargets,
               this.shadowCompositeRenderer,
               this.customUniforms,
               programSet.getPack().hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS)
            );
         } else {
            this.shadowRenderer = null;
         }

         this.defaultFBShadow = this.shadowRenderTargets.createFramebufferWritingToMain(new int[]{0});
      } else {
         this.shadowClearPasses = ImmutableList.of();
         this.shadowClearPassesFull = ImmutableList.of();
         this.shadowCompositeRenderer = null;
         this.shadowRenderer = null;
      }

      this.setup = this.createSetupComputes(programSet.getSetup(), programSet, TextureStage.SETUP);
      this.customUniforms.optimise();
      boolean hasRun = false;
      this.clearPassesFull = ClearPassCreator.createClearPasses(this.renderTargets, true, programSet.getPackDirectives().getRenderTargetDirectives());
      this.clearPasses = ClearPassCreator.createClearPasses(this.renderTargets, false, programSet.getPackDirectives().getRenderTargetDirectives());

      for (ComputeProgram program : this.setup) {
         if (program != null) {
            if (!hasRun) {
               hasRun = true;
               this.renderTargets.onFullClear();
               Vector3d fogColor3 = CapturedRenderingState.INSTANCE.getFogColor();
               Vector4f fogColor = new Vector4f((float)fogColor3.x, (float)fogColor3.y, (float)fogColor3.z, 1.0F);
               this.clearPassesFull.forEach(clearPass -> clearPass.execute(fogColor));
            }

            program.use();
            program.dispatch(1.0F, 1.0F);
         }
      }

      if (hasRun) {
         ComputeProgram.unbind();
      }

      if (programSet.getPackDirectives().supportsColorCorrection()) {
         this.colorSpaceConverter = new ColorSpaceConverter() {
            @Override
            public void rebuildProgram(int width, int height, ColorSpace colorSpace) {
            }

            @Override
            public void process(GlTexture target) {
            }
         };
      } else {
         this.colorSpaceConverter = new ColorSpaceFragmentConverter(main.width, main.height, IrisVideoSettings.colorSpace);
      }

      this.currentColorSpace = IrisVideoSettings.colorSpace;
      int defaultTex = this.packDirectives.getFallbackTex();
      this.defaultFB = this.flippedAfterPrepare.contains(defaultTex)
         ? this.renderTargets.createFramebufferWritingToAlt(new int[]{defaultTex})
         : this.renderTargets.createFramebufferWritingToMain(new int[]{defaultTex});
      this.defaultFBAlt = this.flippedAfterTranslucent.contains(defaultTex)
         ? this.renderTargets.createFramebufferWritingToAlt(new int[]{defaultTex})
         : this.renderTargets.createFramebufferWritingToMain(new int[]{defaultTex});
   }

   private ComputeProgram[] createShadowComputes(ComputeSource[] compute, ProgramSet programSet) {
      ComputeProgram[] programs = new ComputeProgram[compute.length];

      for (int i = 0; i < programs.length; i++) {
         ComputeSource source = compute[i];
         if (source != null && !source.getSource().isEmpty()) {
            ProgramBuilder builder;
            try {
               String transformed = TransformPatcher.patchCompute(
                  source.getName(),
                  source.getSource().orElse(null),
                  TextureStage.GBUFFERS_AND_SHADOW,
                  this.customTextureMap,
                  this.getTextureOverrides(TextureStage.GBUFFERS_AND_SHADOW)
               );
               ShaderPrinter.printProgram(source.getName()).addSource(PatchShaderType.COMPUTE, transformed).print();
               builder = ProgramBuilder.beginCompute(source.getName(), transformed, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
            } catch (ShaderCompileException e) {
               throw e;
            } catch (RuntimeException e) {
               throw new RuntimeException("Shader compilation failed for compute " + source.getName() + "!", e);
            }

            CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
            this.customUniforms.assignTo(builder);
            Supplier<ImmutableSet<Integer>> flipped = () -> this.flippedBeforeShadow;
            TextureStage textureStage = TextureStage.GBUFFERS_AND_SHADOW;
            ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(
               builder, this.customTextureManager.getCustomTextureIdMap(textureStage)
            );
            IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, this.renderTargets, false, this);
            IrisSamplers.addCustomTextures(builder, this.customTextureManager.getIrisCustomTextures());
            IrisSamplers.addCustomImages(customTextureSamplerInterceptor, this.customImages);
            IrisImages.addRenderTargetImages(builder, flipped, this.renderTargets);
            IrisImages.addCustomImages(builder, this.customImages);
            IrisSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, this.whitePixel, true, true, false);
            IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, this.customTextureManager.getNoiseTexture());
            if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor) && this.shadowRenderTargets != null) {
               IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, this.shadowRenderTargets, null, this.separateHardwareSamplers);
               IrisImages.addShadowColorImages(builder, this.shadowRenderTargets, null);
            }

            programs[i] = builder.buildCompute();
            this.customUniforms.mapholderToPass(builder, programs[i]);
            programs[i]
               .setWorkGroupInfo(
                  source.getWorkGroupRelative(),
                  source.getWorkGroups(),
                  FilledIndirectPointer.basedOff(this.shaderStorageBufferHolder, source.getIndirectPointer())
               );
         }
      }

      return programs;
   }

   private ComputeProgram[] createSetupComputes(ComputeSource[] compute, ProgramSet programSet, TextureStage stage) {
      ComputeProgram[] programs = new ComputeProgram[compute.length];

      for (int i = 0; i < programs.length; i++) {
         ComputeSource source = compute[i];
         if (source != null && !source.getSource().isEmpty()) {
            ProgramBuilder builder;
            try {
               String transformed = TransformPatcher.patchCompute(
                  source.getName(), source.getSource().orElse(null), stage, this.customTextureMap, this.getTextureOverrides(stage)
               );
               ShaderPrinter.printProgram(source.getName()).addSource(PatchShaderType.COMPUTE, transformed).print();
               builder = ProgramBuilder.beginCompute(source.getName(), transformed, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
            } catch (RuntimeException e) {
               throw new RuntimeException("Shader compilation failed for setup compute " + source.getName() + "!", e);
            }

            CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
            this.customUniforms.assignTo(builder);
            ImmutableSet<Integer> empty = ImmutableSet.of();
            Supplier<ImmutableSet<Integer>> flipped = () -> empty;
            TextureStage textureStage = TextureStage.SETUP;
            ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(
               builder, this.customTextureManager.getCustomTextureIdMap(textureStage)
            );
            IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, this.renderTargets, true, this);
            IrisSamplers.addCustomTextures(builder, this.customTextureManager.getIrisCustomTextures());
            IrisSamplers.addCompositeSamplers(builder, this.renderTargets);
            IrisSamplers.addCustomImages(customTextureSamplerInterceptor, this.customImages);
            IrisImages.addRenderTargetImages(builder, flipped, this.renderTargets);
            IrisImages.addCustomImages(builder, this.customImages);
            IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, this.customTextureManager.getNoiseTexture());
            if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor) && this.shadowRenderTargets != null) {
               IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, this.shadowRenderTargets, null, this.separateHardwareSamplers);
               IrisImages.addShadowColorImages(builder, this.shadowRenderTargets, null);
            }

            programs[i] = builder.buildCompute();
            this.customUniforms.mapholderToPass(builder, programs[i]);
            programs[i]
               .setWorkGroupInfo(
                  source.getWorkGroupRelative(),
                  source.getWorkGroups(),
                  FilledIndirectPointer.basedOff(this.shaderStorageBufferHolder, source.getIndirectPointer())
               );
         }
      }

      return programs;
   }

   private ShaderSupplier createShader(String name, Optional<ProgramSource> source, ShaderKey key, Patch patch) throws IOException {
      return source.isEmpty()
         ? this.createFallbackShader(name, key)
         : this.createShader(
            name,
            key,
            source.get(),
            key.getProgram(),
            key.getAlphaTest(),
            key.getVertexFormat(),
            key.getFogMode(),
            key.isIntensity(),
            key.shouldIgnoreLightmap(),
            key.isGlint(),
            key.isText(),
            false,
            patch
         );
   }

   @Override
   public Set<String> getTextureOverrides(TextureStage stage) {
      return this.customTextureManager.getCustomTextureIdMap(stage).keySet();
   }

   @Override
   public Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> getTextureMap() {
      return this.customTextureMap;
   }

   private ShaderSupplier createShader(
      String name,
      ShaderKey key,
      ProgramSource source,
      ProgramId programId,
      AlphaTest fallbackAlpha,
      VertexFormat vertexFormat,
      FogMode fogMode,
      boolean isIntensity,
      boolean isFullbright,
      boolean isGlint,
      boolean isText,
      boolean isIE,
      Patch patch
   ) throws IOException {
      GlFramebuffer beforeTranslucent = this.renderTargets.createGbufferFramebuffer(this.flippedAfterPrepare, source.getDirectives().getDrawBuffers());
      GlFramebuffer afterTranslucent = this.renderTargets.createGbufferFramebuffer(this.flippedAfterTranslucent, source.getDirectives().getDrawBuffers());
      boolean isLines = programId == ProgramId.Line && this.resolver.has(ProgramId.Line);
      ShaderAttributeInputs inputs = new ShaderAttributeInputs(vertexFormat, isFullbright, isLines, isGlint, isText, isIE);
      Supplier<ImmutableSet<Integer>> flipped = () -> this.isBeforeTranslucent ? this.flippedAfterPrepare : this.flippedAfterTranslucent;
      return ShaderCreator.create(
         this,
         name,
         key,
         source,
         programId,
         beforeTranslucent,
         afterTranslucent,
         fallbackAlpha,
         vertexFormat,
         inputs,
         this.updateNotifier,
         this,
         flipped,
         fogMode,
         isIntensity,
         isFullbright,
         false,
         isLines,
         this.customUniforms,
         patch
      );
   }

   private ShaderSupplier createFallbackShader(String name, ShaderKey key) throws IOException {
      GlFramebuffer beforeTranslucent = this.renderTargets.createGbufferFramebuffer(this.flippedAfterPrepare, new int[]{0});
      GlFramebuffer afterTranslucent = this.renderTargets.createGbufferFramebuffer(this.flippedAfterTranslucent, new int[]{0});
      return ShaderCreator.createFallback(
         name,
         key,
         beforeTranslucent,
         afterTranslucent,
         key.getAlphaTest(),
         key.getVertexFormat(),
         null,
         this,
         key.getFogMode(),
         key == ShaderKey.GLINT,
         key.isText(),
         key.hasDiffuseLighting(),
         key.isIntensity(),
         key.shouldIgnoreLightmap()
      );
   }

   private ShaderSupplier createShadowShader(String name, Optional<ProgramSource> source, ShaderKey key, Patch patchType) throws IOException {
      return source.isEmpty()
         ? this.createFallbackShadowShader(name, key)
         : this.createShadowShader(
            name,
            key,
            source.get(),
            key.getProgram(),
            key.getAlphaTest(),
            key.getVertexFormat(),
            key.isIntensity(),
            key.shouldIgnoreLightmap(),
            key.isText(),
            false,
            patchType
         );
   }

   private ShaderSupplier createFallbackShadowShader(String name, ShaderKey key) throws IOException {
      return ShaderCreator.createFallbackShadow(
         name,
         key,
         this.shadowTargetsSupplier,
         key.getAlphaTest(),
         key.getVertexFormat(),
         BlendModeOverride.OFF,
         this,
         key.getFogMode(),
         key == ShaderKey.GLINT,
         key.isText(),
         key.hasDiffuseLighting(),
         key.isIntensity(),
         key.shouldIgnoreLightmap()
      );
   }

   private ShaderSupplier createShadowShader(
      String name,
      ShaderKey key,
      ProgramSource source,
      ProgramId programId,
      AlphaTest fallbackAlpha,
      VertexFormat vertexFormat,
      boolean isIntensity,
      boolean isFullbright,
      boolean isText,
      boolean isIE,
      Patch patchType
   ) throws IOException {
      boolean isLines = programId == ProgramId.Line && this.resolver.has(ProgramId.Line);
      ShaderAttributeInputs inputs = new ShaderAttributeInputs(vertexFormat, isFullbright, isLines, false, isText, isIE);
      Supplier<ImmutableSet<Integer>> flipped = () -> this.flippedBeforeShadow;
      return ShaderCreator.createShadow(
         this,
         name,
         key,
         source,
         programId,
         this.shadowTargetsSupplier,
         fallbackAlpha,
         vertexFormat,
         inputs,
         this.updateNotifier,
         this,
         flipped,
         FogMode.PER_VERTEX,
         isIntensity,
         isFullbright,
         true,
         isLines,
         this.customUniforms,
         patchType
      );
   }

   public void addGbufferOrShadowSamplers(
      SamplerHolder samplers,
      ImageHolder images,
      Supplier<ImmutableSet<Integer>> flipped,
      boolean isShadowPass,
      boolean hasTexture,
      boolean hasLightmap,
      boolean hasOverlay
   ) {
      TextureStage textureStage = TextureStage.GBUFFERS_AND_SHADOW;
      ProgramSamplers.CustomTextureSamplerInterceptor samplerHolder = ProgramSamplers.customTextureSamplerInterceptor(
         samplers, this.customTextureManager.getCustomTextureIdMap().getOrDefault(textureStage, Object2ObjectMaps.emptyMap())
      );
      IrisSamplers.addRenderTargetSamplers(samplerHolder, flipped, this.renderTargets, false, this);
      IrisSamplers.addCustomTextures(samplerHolder, this.customTextureManager.getIrisCustomTextures());
      IrisImages.addRenderTargetImages(images, flipped, this.renderTargets);
      IrisImages.addCustomImages(images, this.customImages);
      if (!this.shouldBindPBR) {
         this.shouldBindPBR = IrisSamplers.hasPBRSamplers(samplerHolder);
      }

      IrisSamplers.addLevelSamplers(samplers, this, this.whitePixel, hasTexture, hasLightmap, hasOverlay);
      IrisSamplers.addWorldDepthSamplers(samplerHolder, this.renderTargets);
      IrisSamplers.addNoiseSampler(samplerHolder, this.customTextureManager.getNoiseTexture());
      IrisSamplers.addCustomImages(samplerHolder, this.customImages);
      if (IrisSamplers.hasShadowSamplers(samplerHolder)) {
         IrisSamplers.addShadowSamplers(samplerHolder, this.shadowTargetsSupplier.get(), null, this.separateHardwareSamplers);
      }

      if (isShadowPass || IrisImages.hasShadowImages(images)) {
         IrisImages.addShadowColorImages(images, this.shadowTargetsSupplier.get(), null);
      }
   }

   @Override
   public WorldRenderingPhase getPhase() {
      if (this.shouldRemovePhase) {
         this.phase = WorldRenderingPhase.NONE;
         this.shouldRemovePhase = false;
         GLDebug.popGroup();
      }

      return this.overridePhase != null ? this.overridePhase : this.phase;
   }

   public void removePhaseIfNeeded() {
      if (this.shouldRemovePhase) {
         this.phase = WorldRenderingPhase.NONE;
         this.shouldRemovePhase = false;
         GLDebug.popGroup();
      }
   }

   @Override
   public void setPhase(WorldRenderingPhase phase) {
      if (phase == WorldRenderingPhase.NONE) {
         if (this.shouldRemovePhase) {
            GLDebug.popGroup();
         }

         this.shouldRemovePhase = true;
      } else {
         this.shouldRemovePhase = false;
         if (phase != this.phase) {
            GLDebug.popGroup();
            if (phase != WorldRenderingPhase.NONE
               && phase != WorldRenderingPhase.TERRAIN_CUTOUT
               && phase != WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED
               && phase != WorldRenderingPhase.TRIPWIRE) {
               if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
                  GLDebug.pushGroup(phase.ordinal(), "Shadow " + StringUtils.capitalize(phase.name().toLowerCase(Locale.ROOT).replace("_", " ")));
               } else {
                  GLDebug.pushGroup(phase.ordinal(), StringUtils.capitalize(phase.name().toLowerCase(Locale.ROOT).replace("_", " ")));
               }
            }

            this.phase = phase;
            GbufferPrograms.runPhaseChangeNotifier();
         }
      }
   }

   @Override
   public void setOverridePhase(WorldRenderingPhase phase) {
      this.overridePhase = phase;
      GbufferPrograms.runPhaseChangeNotifier();
   }

   @Override
   public int getCurrentNormalTexture() {
      return this.currentNormalTexture == null ? 0 : this.currentNormalTexture.getTexture().iris$getGlId();
   }

   public GlSampler getNormalSampler() {
      return this.normalSampler;
   }

   public GlSampler getSpecularSampler() {
      return this.specularSampler;
   }

   @Override
   public int getCurrentSpecularTexture() {
      return this.currentSpecularTexture == null ? 0 : this.currentSpecularTexture.getTexture().iris$getGlId();
   }

   @Override
   public void onSetAlbedoTex(GpuTextureView id) {
      if (id != null) {
         this.albedoTex = id.texture().iris$getGlId();
         int maxAnisotropy = Minecraft.getInstance().options.textureFiltering().get() == TextureFilteringMethod.ANISOTROPIC
            ? Minecraft.getInstance().options.maxAnisotropyValue()
            : 1;
         if (this.shouldBindPBR && this.isRenderingWorld) {
            PBRTextureHolder pbrHolder = PBRTextureManager.INSTANCE.getOrLoadHolder(id.texture().iris$getGlId());
            this.currentNormalTexture = pbrHolder.normalTexture();
            this.currentSpecularTexture = pbrHolder.specularTexture();
            TextureFormat textureFormat = TextureFormatLoader.getFormat();
            if (textureFormat != null) {
               this.normalSampler = textureFormat.canInterpolateValues(PBRType.NORMAL)
                  ? IrisSamplers.getTerrainCacheIris(maxAnisotropy)
                  : GlSampler.MIPPED_NEAREST_NEAREST;
               this.specularSampler = textureFormat.canInterpolateValues(PBRType.SPECULAR)
                  ? IrisSamplers.getTerrainCacheIris(maxAnisotropy)
                  : GlSampler.MIPPED_NEAREST_NEAREST;
            } else {
               this.normalSampler = IrisSamplers.getTerrainCacheIris(maxAnisotropy);
               this.specularSampler = IrisSamplers.getTerrainCacheIris(maxAnisotropy);
            }

            PBRTextureManager.notifyPBRTexturesChanged();
         }
      }
   }

   @Override
   public void beginLevelRendering() {
      this.isRenderingWorld = true;
      if (!this.initializedBlockIds) {
         WorldRenderingSettings.INSTANCE
            .setBlockStateIds(BlockMaterialMapping.createBlockStateIdMap(this.pack.getIdMap().getBlockProperties(), this.pack.getIdMap().getTagEntries()));
         WorldRenderingSettings.INSTANCE.setBlockTypeIds(BlockMaterialMapping.createBlockTypeMap(this.pack.getIdMap().getBlockRenderTypeMap()));
         Minecraft.getInstance().levelExtractor.allChanged();
         this.initializedBlockIds = true;
      }

      GlStateManager._activeTexture(33984);
      Vector4f emptyClearColor = new Vector4f(1.0F);
      GLDebug.pushGroup(100, "Clear textures");
      this.clearImages.forEach(ImageClearPass::execute);
      if (this.shadowRenderTargets != null) {
         if (this.packDirectives.getShadowDirectives().isShadowEnabled() == OptionalBoolean.FALSE) {
            if (this.shadowRenderTargets.isFullClearRequired()) {
               this.shadowClearPasses = ClearPassCreator.createShadowClearPasses(this.shadowRenderTargets, false, this.shadowDirectives);
               this.shadowClearPassesFull = ClearPassCreator.createShadowClearPasses(this.shadowRenderTargets, true, this.shadowDirectives);
               this.shadowRenderTargets.onFullClear();
               UnmodifiableIterator main = this.shadowClearPassesFull.iterator();

               while (main.hasNext()) {
                  ClearPass clearPass = (ClearPass)main.next();
                  clearPass.execute(emptyClearColor);
               }
            }
         } else {
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(this.shadowRenderTargets.getDepthTexture(), 1.0);

            for (ComputeProgram computeProgram : this.shadowComputes) {
               if (computeProgram != null) {
                  computeProgram.use();
                  this.customUniforms.push(computeProgram);
                  computeProgram.dispatch(this.shadowMapResolution, this.shadowMapResolution);
               }
            }

            ImmutableList<ClearPass> passes;
            if (this.shadowRenderTargets.isFullClearRequired()) {
               this.shadowClearPasses = ClearPassCreator.createShadowClearPasses(this.shadowRenderTargets, false, this.shadowDirectives);
               this.shadowClearPassesFull = ClearPassCreator.createShadowClearPasses(this.shadowRenderTargets, true, this.shadowDirectives);
               passes = this.shadowClearPassesFull;
               this.shadowRenderTargets.onFullClear();
            } else {
               passes = this.shadowClearPasses;
            }

            UnmodifiableIterator var17 = passes.iterator();

            while (var17.hasNext()) {
               ClearPass clearPass = (ClearPass)var17.next();
               clearPass.execute(emptyClearColor);
            }
         }
      }

      PBRTextureManager.INSTANCE.onNewFrame();
      this.updateNotifier.onNewFrame();
      this.customUniforms.update();
      RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      GpuTexture depthTexture = main.getDepthTexture();
      DepthBufferFormat depthBufferFormat = DepthBufferFormat.fromGlEnumOrDefault(GlConst.toGlInternalId(main.getDepthTexture().getFormat()));
      boolean changed = this.renderTargets
         .resizeIfNeeded(
            ((Blaze3dRenderTargetExt)main).iris$getDepthBufferVersion(), depthTexture, main.width, main.height, depthBufferFormat, this.packDirectives
         );
      if (changed) {
         this.beginRenderer.recalculateSizes();
         this.prepareRenderer.recalculateSizes();
         this.deferredRenderer.recalculateSizes();
         this.compositeRenderer.recalculateSizes();
         this.finalPassRenderer.recalculateSwapPassSize();
         if (this.shaderStorageBufferHolder != null) {
            this.shaderStorageBufferHolder.hasResizedScreen(main.width, main.height);
         }

         this.customImages.forEach(image -> image.updateNewSize(main.width, main.height));
         this.clearPassesFull.forEach(clearPassx -> this.renderTargets.destroyFramebuffer(clearPassx.getFramebuffer()));
         this.clearPasses.forEach(clearPassx -> this.renderTargets.destroyFramebuffer(clearPassx.getFramebuffer()));
         this.clearPassesFull = ClearPassCreator.createClearPasses(this.renderTargets, true, this.packDirectives.getRenderTargetDirectives());
         this.clearPasses = ClearPassCreator.createClearPasses(this.renderTargets, false, this.packDirectives.getRenderTargetDirectives());
      }

      if (changed || IrisVideoSettings.colorSpace != this.currentColorSpace) {
         this.currentColorSpace = IrisVideoSettings.colorSpace;
         this.colorSpaceConverter.rebuildProgram(main.width, main.height, this.currentColorSpace);
      }

      ImmutableList<ClearPass> passes;
      if (this.renderTargets.isFullClearRequired()) {
         this.renderTargets.onFullClear();
         passes = this.clearPassesFull;
      } else {
         passes = this.clearPasses;
      }

      Vector3d fogColor3 = CapturedRenderingState.INSTANCE.getFogColor();
      Vector4f fogColor = new Vector4f((float)fogColor3.x, (float)fogColor3.y, (float)fogColor3.z, 1.0F);
      UnmodifiableIterator hasRun = passes.iterator();

      while (hasRun.hasNext()) {
         ClearPass clearPass = (ClearPass)hasRun.next();
         clearPass.execute(fogColor);
      }

      GLDebug.popGroup();
      Minecraft.getInstance().gameRenderer.mainRenderTarget().iris$bindFramebuffer();
      this.isMainBound = true;
      if (changed) {
         boolean hasRunx = false;

         for (ComputeProgram program : this.setup) {
            if (program != null) {
               hasRunx = true;
               program.use();
               program.dispatch(1.0F, 1.0F);
            }
         }

         if (hasRunx) {
            ComputeProgram.unbind();
         }
      }

      this.beginRenderer.renderAll();
      this.isBeforeTranslucent = true;
   }

   @Override
   public void renderShadows(LevelRendererAccessor worldRenderer, Camera playerCamera, CameraRenderState renderState) {
      if (this.shadowRenderer != null) {
         this.shadowRenderer.renderShadows(worldRenderer, playerCamera, renderState);
      }

      this.prepareRenderer.renderAll();
   }

   @Override
   public void addDebugText(DebugScreenDisplayer messages) {
      if (this.shadowRenderer != null) {
         this.shadowRenderer.addDebugText(messages);
      } else {
         messages.addLine("[Iris] Shadow Maps: not used by shader pack");
      }
   }

   @Override
   public OptionalInt getForcedShadowRenderDistanceChunksForDisplay() {
      return this.forcedShadowRenderDistanceChunks;
   }

   @Override
   public void beginHand() {
      this.centerDepthSampler.sampleCenterDepth();
      this.renderTargets.copyPreHandDepth();
   }

   @Override
   public void beginTranslucents() {
      if (this.destroyed) {
         throw new IllegalStateException("Tried to use a destroyed world rendering pipeline");
      }

      this.removePhaseIfNeeded();
      this.isBeforeTranslucent = false;
      this.renderTargets.copyPreTranslucentDepth();
      net.irisshaders.iris.vulkan.IrisVulkanRenderer.get().executeFrameStage(net.irisshaders.iris.pipeline.CompositePass.DEFERRED);
      this.deferredRenderer.renderAll();
   }

   @Override
   public void finalizeLevelRendering() {
      this.isRenderingWorld = false;
      this.removePhaseIfNeeded();
      net.irisshaders.iris.vulkan.IrisVulkanRenderer.get().executeFrameStage(net.irisshaders.iris.pipeline.CompositePass.COMPOSITE);
      this.compositeRenderer.renderAll();
      net.irisshaders.iris.vulkan.IrisVulkanRenderer.get().renderFinalPass();
      this.finalPassRenderer.renderFinalPass();
   }

   @Override
   public void finalizeGameRendering() {
      this.colorSpaceConverter.process((GlTexture)Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture());
   }

   @Override
   public boolean shouldDisableVanillaEntityShadows() {
      return this.shadowRenderer != null;
   }

   @Override
   public boolean shouldRenderUnderwaterOverlay() {
      return this.shouldRenderUnderwaterOverlay;
   }

   @Override
   public boolean shouldRenderVignette() {
      return this.shouldRenderVignette;
   }

   @Override
   public boolean shouldRenderSun() {
      return this.shouldRenderSun;
   }

   @Override
   public boolean shouldRenderWeather() {
      return this.shouldRenderWeather;
   }

   @Override
   public boolean shouldRenderWeatherParticles() {
      return this.shouldRenderWeatherParticles;
   }

   @Override
   public boolean shouldRenderMoon() {
      return this.shouldRenderMoon;
   }

   @Override
   public boolean shouldRenderStars() {
      return this.shouldRenderStars;
   }

   @Override
   public boolean shouldRenderSkyDisc() {
      return this.shouldRenderSkyDisc;
   }

   @Override
   public boolean shouldWriteRainAndSnowToDepthBuffer() {
      return this.shouldWriteRainAndSnowToDepthBuffer;
   }

   @Override
   public ParticleRenderingSettings getParticleRenderingSettings() {
      return this.particleRenderingSettings;
   }

   @Override
   public boolean allowConcurrentCompute() {
      return this.allowConcurrentCompute;
   }

   @Override
   public boolean hasFeature(FeatureFlags flag) {
      return this.pack.hasFeature(flag);
   }

   @Override
   public boolean shouldDisableDirectionalShading() {
      return !this.oldLighting;
   }

   @Override
   public boolean shouldDisableFrustumCulling() {
      return !this.frustumCulling;
   }

   @Override
   public boolean shouldDisableOcclusionCulling() {
      return !this.occlusionCulling;
   }

   @Override
   public CloudSetting getCloudSetting() {
      return this.cloudSetting;
   }

   @Override
   public ShaderMap getShaderMap() {
      return this.shaderMap;
   }

   private void destroyShaders() {
      this.loadedShaders.forEach(shader -> shader.close());
   }

   @Override
   public void destroy() {
      this.destroyed = true;
      net.irisshaders.iris.vulkan.IrisVulkanRenderer.get().destroy();
      this.destroyShaders();

      for (int i = 0; i < 16; i++) {
         GlStateManager._activeTexture(33984 + i);
         IrisRenderSystem.unbindAllSamplers();
         GlStateManager._bindTexture(0);
      }

      GlStateManager._activeTexture(33984);
      if (this.shadowCompositeRenderer != null) {
         this.shadowCompositeRenderer.destroy();
      }

      this.prepareRenderer.destroy();
      this.compositeRenderer.destroy();
      this.deferredRenderer.destroy();
      this.finalPassRenderer.destroy();
      this.centerDepthSampler.destroy();
      this.customTextureManager.destroy();
      this.whitePixel.close();
      this.biggerWhitePixel.close();
      this.horizonRenderer.destroy();
      GlStateManager._glBindFramebuffer(36008, 0);
      GlStateManager._glBindFramebuffer(36009, 0);
      GlStateManager._glBindFramebuffer(36160, 0);
      this.renderTargets.destroy();
      this.dhCompat.clearPipeline();
      this.clearImages.forEach(ImageClearPass::destroy);
      this.customImages.forEach(GlResource::destroy);
      if (this.shadowRenderTargets != null) {
         this.shadowRenderTargets.destroy();
      }

      if (this.shadowRenderer != null) {
         this.shadowRenderer.destroy();
      }

      if (this.shaderStorageBufferHolder != null) {
         this.shaderStorageBufferHolder.destroyBuffers();
      }
   }

   @Override
   public boolean shouldOverrideShaders() {
      return this.isRenderingWorld && this.isMainBound;
   }

   @Override
   public FrameUpdateNotifier getFrameUpdateNotifier() {
      return this.updateNotifier;
   }

   @Override
   public float getSunPathRotation() {
      return this.sunPathRotation;
   }

   @Override
   public DHCompat getDHCompat() {
      return this.dhCompat;
   }

   public AbstractTexture getWhitePixel() {
      return this.whitePixel;
   }

   public AbstractTexture getBiggerWhitePixel() {
      return this.biggerWhitePixel;
   }

   @Override
   public void setIsMainBound(boolean bound) {
      this.isMainBound = bound;
   }

   @Override
   public void onBeginClear() {
      this.setPhase(WorldRenderingPhase.SKY);
      Skybox skyType = Minecraft.getInstance().level.dimensionType().skybox();
      if (this.shouldRenderSkyDisc && (skyType == Skybox.OVERWORLD || Minecraft.getInstance().level.dimensionType().hasSkyLight())) {
         Vector3d fogColor3 = CapturedRenderingState.INSTANCE.getFogColor();
         Vector4f fogColor = new Vector4f((float)fogColor3.x, (float)fogColor3.y, (float)fogColor3.z, 1.0F);
         this.horizonRenderer
            .renderHorizon(CapturedRenderingState.INSTANCE.getGbufferModelView(), CapturedRenderingState.INSTANCE.getGbufferProjection(), fogColor);
      }
   }

   @Override
   public boolean supportsEndFlash() {
      return this.supportsEndFlash;
   }

   @Override
   public int getAlbedoTex() {
      return this.albedoTex;
   }

   public Optional<ProgramSource> getDHTerrainShader() {
      return this.resolver.resolve(ProgramId.DhTerrain);
   }

   public Optional<ProgramSource> getDHGenericShader() {
      return this.resolver.resolve(ProgramId.DhGeneric);
   }

   public Optional<ProgramSource> getDHWaterShader() {
      return this.resolver.resolve(ProgramId.DhWater);
   }

   public Optional<ProgramSource> getDHShadowShader() {
      return this.resolver.resolve(ProgramId.DhShadow);
   }

   public CustomUniforms getCustomUniforms() {
      return this.customUniforms;
   }

   public GlFramebuffer createDHFramebuffer(ProgramSource sources, boolean trans) {
      return this.renderTargets.createDHFramebuffer(trans ? this.flippedAfterTranslucent : this.flippedAfterPrepare, sources.getDirectives().getDrawBuffers());
   }

   public ImmutableSet<Integer> getFlippedBeforeShadow() {
      return this.flippedBeforeShadow;
   }

   public ImmutableSet<Integer> getFlippedAfterPrepare() {
      return this.flippedAfterPrepare;
   }

   public ImmutableSet<Integer> getFlippedAfterTranslucent() {
      return this.flippedAfterTranslucent;
   }

   public GlFramebuffer createDHFramebufferShadow(ProgramSource sources) {
      return this.shadowRenderTargets.createDHFramebuffer(ImmutableSet.of(), new int[]{0, 1});
   }

   public boolean hasShadowRenderTargets() {
      return this.shadowRenderTargets != null;
   }

   public boolean skipAllRendering() {
      return this.skipAllRendering;
   }

   public CloudSetting getDHCloudSetting() {
      return this.dhCloudSetting;
   }

   public void bindDefault() {
      if (this.isBeforeTranslucent) {
         this.defaultFB.bind();
      } else {
         this.defaultFBAlt.bind();
      }
   }

   public void bindDefaultShadow() {
      this.defaultFBShadow.bind();
   }
}
