package net.irisshaders.iris.shadows;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider;
import net.caffeinemc.mods.sodium.client.util.FogStorage;
import net.caffeinemc.mods.sodium.client.util.SodiumChunkSection;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;
import net.caffeinemc.mods.sodium.mixin.core.render.world.FrustumAccessor;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.mixinterface.ShadowRenderListAccess;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackShadowDirectives;
import net.irisshaders.iris.shaderpack.properties.ShadowCullState;
import net.irisshaders.iris.shadows.frustum.BoxCuller;
import net.irisshaders.iris.shadows.frustum.CullEverythingFrustum;
import net.irisshaders.iris.shadows.frustum.FrustumHolder;
import net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.irisshaders.iris.shadows.frustum.advanced.SafeZoneCullingFrustum;
import net.irisshaders.iris.shadows.frustum.fallback.BoxCullingFrustum;
import net.irisshaders.iris.shadows.frustum.fallback.NonCullingFrustum;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.CelestialUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class ShadowRenderer {
   public static int RESOLUTION;
   public static boolean ACTIVE = false;
   public static List<BlockEntity> visibleBlockEntities;
   public static int renderDistance;
   public static Matrix4f MODELVIEW;
   public static Matrix4f PROJECTION;
   public static Frustum FRUSTUM;
   private final float halfPlaneLength;
   private final float nearPlane;
   private final float farPlane;
   private final float voxelDistance;
   private final float renderDistanceMultiplier;
   private final float entityShadowDistanceMultiplier;
   private final int resolution;
   private final float intervalSize;
   private final Float fov;
   private final ShadowRenderTargets targets;
   private final ShadowCullState packCullingState;
   private final ShadowCompositeRenderer compositeRenderer;
   private final boolean shouldRenderTerrain;
   private final boolean shouldRenderTranslucent;
   private final boolean shouldRenderEntities;
   private final boolean shouldRenderPlayer;
   private final boolean shouldRenderBlockEntities;
   private final boolean shouldRenderDH;
   private final float sunPathRotation;
   private final RenderBuffers buffers;
   private final List<ShadowRenderer.MipmapPass> mipmapPasses = new ArrayList<>();
   private final String debugStringOverall;
   private final boolean separateHardwareSamplers;
   private final boolean shouldRenderLightBlockEntities;
   private final IrisRenderingPipeline pipeline;
   private boolean packHasVoxelization;
   private FrustumHolder terrainFrustumHolder;
   private FrustumHolder entityFrustumHolder;
   private String debugStringTerrain = "(unavailable)";
   private int renderedShadowEntities = 0;
   private int renderedShadowBlockEntities = 0;
   private final LevelRenderState levelRenderState;
   private final SubmitNodeStorage submitNodeStorage;
   private final FeatureRenderDispatcher featureRenderDispatcher;

   public ShadowRenderer(
      IrisRenderingPipeline pipeline,
      ProgramSource shadow,
      PackDirectives directives,
      ShadowRenderTargets shadowRenderTargets,
      ShadowCompositeRenderer compositeRenderer,
      CustomUniforms customUniforms,
      boolean separateHardwareSamplers
   ) {
      this.pipeline = pipeline;
      this.separateHardwareSamplers = separateHardwareSamplers;
      PackShadowDirectives shadowDirectives = directives.getShadowDirectives();
      this.halfPlaneLength = shadowDirectives.getDistance();
      this.nearPlane = shadowDirectives.getNearPlane();
      this.farPlane = shadowDirectives.getFarPlane();
      this.voxelDistance = shadowDirectives.getVoxelDistance();
      this.renderDistanceMultiplier = shadowDirectives.getDistanceRenderMul();
      this.entityShadowDistanceMultiplier = shadowDirectives.getEntityShadowDistanceMul();
      this.resolution = shadowDirectives.getResolution();
      this.intervalSize = shadowDirectives.getIntervalSize();
      this.shouldRenderTerrain = shadowDirectives.shouldRenderTerrain();
      this.shouldRenderTranslucent = shadowDirectives.shouldRenderTranslucent();
      this.shouldRenderEntities = shadowDirectives.shouldRenderEntities();
      this.shouldRenderPlayer = shadowDirectives.shouldRenderPlayer();
      this.shouldRenderBlockEntities = shadowDirectives.shouldRenderBlockEntities();
      this.shouldRenderLightBlockEntities = shadowDirectives.shouldRenderLightBlockEntities();
      this.shouldRenderDH = shadowDirectives.isDhShadowEnabled().orElse(false);
      this.compositeRenderer = compositeRenderer;
      this.debugStringOverall = "half plane = " + this.halfPlaneLength + " meters @ " + this.resolution + "x" + this.resolution;
      this.terrainFrustumHolder = new FrustumHolder();
      this.entityFrustumHolder = new FrustumHolder();
      this.fov = shadowDirectives.getFov();
      this.targets = shadowRenderTargets;
      if (shadow != null) {
         this.packHasVoxelization = shadow.getGeometrySource().isPresent();
         this.packCullingState = shadowDirectives.getCullingState();
      } else {
         this.packHasVoxelization = false;
         this.packCullingState = ShadowCullState.DEFAULT;
      }

      this.sunPathRotation = directives.getSunPathRotation();
      int processors = Runtime.getRuntime().availableProcessors();
      this.buffers = new RenderBuffers(processors);
      this.configureSamplingSettings(shadowDirectives);
      this.levelRenderState = new LevelRenderState();
      this.submitNodeStorage = new SubmitNodeStorage();
      this.featureRenderDispatcher = new FeatureRenderDispatcher(
         this.buffers,
         Minecraft.getInstance().getModelManager(),
         Minecraft.getInstance().getAtlasManager(),
         Minecraft.getInstance().font,
         Minecraft.getInstance().gameRenderer.gameRenderState()
      );
   }

   public static PoseStack createShadowModelView(float sunPathRotation, float intervalSize, float nearPlane, float farPlane) {
      Vector3d cameraPos = CameraUniforms.getUnshiftedCameraPosition();
      double cameraX = cameraPos.x;
      double cameraY = cameraPos.y;
      double cameraZ = cameraPos.z;
      PoseStack modelView = new PoseStack();
      ShadowMatrices.createModelViewMatrix(modelView, getShadowAngle(), intervalSize, sunPathRotation, cameraX, cameraY, cameraZ, nearPlane, farPlane);
      return modelView;
   }

   private static ClientLevel getLevel() {
      return Objects.requireNonNull(Minecraft.getInstance().level);
   }

   public static float getSunAngle(boolean sun) {
      float currentAngle = (Float)Minecraft.getInstance()
         .gameRenderer
         .mainCamera()
         .attributeProbe()
         .getValue(sun ? EnvironmentAttributes.SUN_ANGLE : EnvironmentAttributes.MOON_ANGLE, CapturedRenderingState.INSTANCE.getTickDelta());
      float c = currentAngle + 90.0F;
      if (c < 0.0F) {
         c += 360.0F;
      } else if (c > 360.0F) {
         c -= 360.0F;
      }

      return c;
   }

   private static float getShadowAngle() {
      float shadowAngle = getSunAngle(CelestialUniforms.isDay());
      return shadowAngle / 360.0F;
   }

   public void setUsesImages(boolean usesImages) {
      this.packHasVoxelization = this.packHasVoxelization || usesImages;
   }

   private void configureSamplingSettings(PackShadowDirectives shadowDirectives) {
      ImmutableList<PackShadowDirectives.DepthSamplingSettings> depthSamplingSettings = shadowDirectives.getDepthSamplingSettings();
      Int2ObjectMap<PackShadowDirectives.SamplingSettings> colorSamplingSettings = shadowDirectives.getColorSamplingSettings();
      GlStateManager._activeTexture(33988);
      this.configureDepthSampler(this.targets.getDepthTexture().iris$getGlId(), (PackShadowDirectives.DepthSamplingSettings)depthSamplingSettings.get(0));
      this.configureDepthSampler(
         this.targets.getDepthTextureNoTranslucents().iris$getGlId(), (PackShadowDirectives.DepthSamplingSettings)depthSamplingSettings.get(1)
      );

      for (int i = 0; i < this.targets.getNumColorTextures(); i++) {
         if (this.targets.get(i) != null) {
            int glTextureId = this.targets.get(i).getMainTexture();
            this.configureSampler(
               glTextureId, (PackShadowDirectives.SamplingSettings)colorSamplingSettings.computeIfAbsent(i, a -> new PackShadowDirectives.SamplingSettings())
            );
         }
      }

      GlStateManager._activeTexture(33984);
   }

   private void configureDepthSampler(int glTextureId, PackShadowDirectives.DepthSamplingSettings settings) {
      if (settings.getHardwareFiltering() && !this.separateHardwareSamplers) {
         IrisRenderSystem.texParameteri(glTextureId, 3553, 34892, 34894);
         IrisRenderSystem.texParameteri(glTextureId, 3553, 34893, 518);
      }

      IrisRenderSystem.texParameteriv(glTextureId, 3553, 36422, new int[]{6403, 6403, 6403, 1});
      this.configureSampler(glTextureId, settings);
   }

   private void configureSampler(int glTextureId, PackShadowDirectives.SamplingSettings settings) {
      if (settings.getMipmap()) {
         int filteringMode = settings.getNearest() ? 9984 : 9987;
         this.mipmapPasses.add(new ShadowRenderer.MipmapPass(glTextureId, filteringMode));
      }

      if (!settings.getNearest()) {
         IrisRenderSystem.texParameteri(glTextureId, 3553, 10241, 9729);
         IrisRenderSystem.texParameteri(glTextureId, 3553, 10240, 9729);
      } else {
         IrisRenderSystem.texParameteri(glTextureId, 3553, 10241, 9728);
         IrisRenderSystem.texParameteri(glTextureId, 3553, 10240, 9728);
      }
   }

   private void generateMipmaps() {
      GlStateManager._activeTexture(33988);

      for (ShadowRenderer.MipmapPass mipmapPass : this.mipmapPasses) {
         this.setupMipmappingForTexture(mipmapPass.texture(), mipmapPass.targetFilteringMode());
      }

      GlStateManager._activeTexture(33984);
   }

   private void setupMipmappingForTexture(int texture, int filteringMode) {
      IrisRenderSystem.generateMipmaps(texture, 3553);
      IrisRenderSystem.texParameteri(texture, 3553, 10241, filteringMode);
   }

   private FrustumHolder createShadowFrustum(float renderMultiplier, FrustumHolder holder) {
      if ((this.packCullingState != ShadowCullState.DEFAULT || !this.packHasVoxelization) && this.packCullingState != ShadowCullState.DISTANCE) {
         boolean hasSafeZone = this.packCullingState == ShadowCullState.SAFE_ZONE;
         if (hasSafeZone && renderMultiplier < 0.0F) {
            renderMultiplier = 1.0F;
         }

         double distance = (hasSafeZone ? this.voxelDistance : this.halfPlaneLength) * renderMultiplier;
         String setter = "(set by shader pack)";
         if (renderMultiplier < 0.0F) {
            distance = IrisVideoSettings.shadowDistance * 16;
            setter = "(set by user)";
         }

         String distanceInfo;
         BoxCuller boxCuller;
         if (distance >= Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 && !hasSafeZone) {
            distanceInfo = "render distance = " + Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 + " blocks ";
            distanceInfo = distanceInfo
               + (Minecraft.getInstance().isLocalServer() ? "(capped by normal render distance)" : "(capped by normal/server render distance)");
            boxCuller = null;
         } else {
            distanceInfo = distance + " blocks " + setter;
            if (distance == 0.0 && !hasSafeZone) {
               String cullingInfo = "no shadows rendered";
               holder.setInfo(new CullEverythingFrustum(), distanceInfo, cullingInfo);
            }

            boxCuller = new BoxCuller(distance);
         }

         String cullingInfo = (hasSafeZone ? "Safe Zone" : "Advanced") + " Frustum Culling enabled";
         Vector4f shadowLightPosition = new CelestialUniforms(this.sunPathRotation).getShadowLightPositionInWorldSpace();
         Vector3f shadowLightVectorFromOrigin = new Vector3f(shadowLightPosition.x(), shadowLightPosition.y(), shadowLightPosition.z());
         shadowLightVectorFromOrigin.normalize();
         Matrix4f projView = (this.shouldRenderDH && DHCompat.hasRenderingEnabled()
               ? DHCompat.getProjection()
               : CapturedRenderingState.INSTANCE.getGbufferProjection())
            .mul(CapturedRenderingState.INSTANCE.getGbufferModelView(), new Matrix4f());
         return hasSafeZone
            ? holder.setInfo(
               new SafeZoneCullingFrustum(projView, PROJECTION, shadowLightVectorFromOrigin, boxCuller, new BoxCuller(this.halfPlaneLength * renderMultiplier)),
               distanceInfo,
               cullingInfo
            )
            : holder.setInfo(new AdvancedShadowCullingFrustum(projView, PROJECTION, shadowLightVectorFromOrigin, boxCuller), distanceInfo, cullingInfo);
      } else {
         double distance = this.halfPlaneLength * renderMultiplier;
         String reason;
         if (this.packCullingState == ShadowCullState.DISTANCE) {
            reason = "(set by shader pack)";
         } else {
            reason = "(voxelization detected)";
         }

         if (!(distance <= 0.0) && !(distance > Minecraft.getInstance().options.getEffectiveRenderDistance() * 16)) {
            String distanceInfo = distance + " blocks (set by shader pack)";
            String cullingInfo = "distance only " + reason;
            BoxCuller boxCuller = new BoxCuller(distance);
            holder.setInfo(new BoxCullingFrustum(boxCuller), distanceInfo, cullingInfo);
            return holder;
         } else {
            String distanceInfo = "render distance = " + Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 + " blocks ";
            distanceInfo = distanceInfo
               + (Minecraft.getInstance().isLocalServer() ? "(capped by normal render distance)" : "(capped by normal/server render distance)");
            String cullingInfo = "disabled " + reason;
            return holder.setInfo(new NonCullingFrustum(), distanceInfo, cullingInfo);
         }
      }
   }

   public void setupShadowViewport() {
      GlStateManager._viewport(0, 0, this.resolution, this.resolution);
   }

   public void renderShadows(LevelRendererAccessor levelRenderer, Camera playerCamera, CameraRenderState renderState) {
      if (IrisVideoSettings.getOverriddenShadowDistance(IrisVideoSettings.shadowDistance) != 0) {
         GpuSampler theSampler = RenderSystem.getSamplerCache()
            .getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, true);
         playerCamera.extractRenderState(this.levelRenderState.cameraRenderState, Minecraft.getInstance().getDeltaTracker());
         Minecraft client = Minecraft.getInstance();
         ProfilerFiller profiler = Profiler.get();
         profiler.popPush("shadows");
         ACTIVE = true;
         RESOLUTION = this.resolution;
         renderDistance = (int)(this.halfPlaneLength * this.renderDistanceMultiplier / 16.0F);
         if (this.renderDistanceMultiplier < 0.0F) {
            renderDistance = IrisVideoSettings.shadowDistance;
         }

         visibleBlockEntities = new ArrayList<>();
         RenderBuffers playerBuffers = levelRenderer.getRenderBuffers();
         levelRenderer.setRenderBuffers(this.buffers);
         visibleBlockEntities = new ArrayList<>();
         this.setupShadowViewport();
         PoseStack modelView = createShadowModelView(this.sunPathRotation, this.intervalSize, this.nearPlane, this.farPlane);
         MODELVIEW = new Matrix4f(modelView.last().pose());
         Matrix4f cameraViewRotation = new Matrix4f(this.levelRenderState.cameraRenderState.viewRotationMatrix);
         Matrix4f cameraProjection = new Matrix4f(this.levelRenderState.cameraRenderState.projectionMatrix);
         this.levelRenderState.cameraRenderState.viewRotationMatrix = MODELVIEW;
         RenderSystem.getModelViewStack().pushMatrix();
         RenderSystem.getModelViewStack().set(MODELVIEW);
         Matrix4f shadowProjection;
         if (this.fov != null) {
            shadowProjection = ShadowMatrices.createPerspectiveMatrix(this.fov);
         } else {
            shadowProjection = ShadowMatrices.createOrthoMatrix(
               this.halfPlaneLength,
               Mth.equal(this.nearPlane, -1.0F) ? -DHCompat.getRenderDistance() * 16 : this.nearPlane,
               Mth.equal(this.farPlane, -1.0F) ? DHCompat.getRenderDistance() * 16 : this.farPlane
            );
         }

         this.levelRenderState.cameraRenderState.projectionMatrix = shadowProjection;
         IrisRenderSystem.setShadowProjection(shadowProjection);
         PROJECTION = shadowProjection;
         profiler.push("terrain_setup");
         if (levelRenderer instanceof CullingDataCache) {
            ((CullingDataCache)levelRenderer).saveState();
         }

         profiler.push("initialize frustum");
         this.terrainFrustumHolder = this.createShadowFrustum(this.renderDistanceMultiplier, this.terrainFrustumHolder);
         FRUSTUM = this.terrainFrustumHolder.getFrustum();
         Vector3d cameraPos = CameraUniforms.getUnshiftedCameraPosition();
         double cameraX = cameraPos.x();
         double cameraY = cameraPos.y();
         double cameraZ = cameraPos.z();
         this.terrainFrustumHolder.getFrustum().prepare(cameraX, cameraY, cameraZ);
         profiler.pop();
         boolean wasChunkCullingEnabled = client.smartCull;
         client.smartCull = false;
         ChunkRenderMatrices playerMatrices = ((LevelRendererExtension)levelRenderer).sodium$getMatrices();
         ((LevelRendererExtension)levelRenderer).sodium$setMatrices(new ChunkRenderMatrices(shadowProjection, MODELVIEW));
         SodiumWorldRenderer sodiumWorldRenderer = ((LevelRendererExtension)levelRenderer).sodium$getWorldRenderer();
         if (sodiumWorldRenderer instanceof ShadowRenderListAccess shadowRenderListAccess) {
            shadowRenderListAccess.iris$beginShadowRenderListScope();
         }

         try {
            sodiumWorldRenderer.scheduleTerrainUpdate();
            Frustum shadowFrustum = this.terrainFrustumHolder.getFrustum();
            sodiumWorldRenderer.setupTerrain(
               playerCamera,
               ((ViewportProvider)shadowFrustum).sodium$createViewport(),
               ((FogStorage)client.gameRenderer).sodium$getFogParameters(),
               playerCamera.entity() != null && playerCamera.entity().isSpectator(),
               false,
               ((FrustumAccessor)shadowFrustum).sodium$getMatrix()
            );
            client.smartCull = wasChunkCullingEnabled;
            profiler.popPush("terrain");
            GlStateManager._disableCull();
            ChunkSectionsToRender sections = new SodiumChunkSection(
               ((LevelRendererExtension)levelRenderer).sodium$getWorldRenderer(),
               ((LevelRendererExtension)levelRenderer).sodium$getMatrices(),
               cameraX,
               cameraY,
               cameraZ
            );
            SodiumWorldRenderer.instance()
               .prepareChunkRendering(
                  ((LevelRendererExtension)levelRenderer).sodium$getMatrices(),
                  this.levelRenderState.cameraRenderState.pos.x,
                  this.levelRenderState.cameraRenderState.pos.y,
                  this.levelRenderState.cameraRenderState.pos.z
               );
            GpuTextureView atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getTextureView();
            if (this.shouldRenderTerrain) {
               this.pipeline.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
               RenderPass renderPass = RenderSystem.getDevice()
                  .createCommandEncoder()
                  .createRenderPass(
                     () -> "Terrain",
                     Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(),
                     Optional.empty(),
                     Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView(),
                     OptionalDouble.empty()
                  );

               try {
                  sections.renderGroup(ChunkSectionLayerGroup.OPAQUE, renderPass, theSampler, atlas, false);
               } catch (Throwable var47) {
                  if (renderPass != null) {
                     try {
                        renderPass.close();
                     } catch (Throwable var44) {
                        var47.addSuppressed(var44);
                     }
                  }

                  throw var47;
               }

               if (renderPass != null) {
                  renderPass.close();
               }

               this.pipeline.setPhase(WorldRenderingPhase.NONE);
            }

            if (!ShadowRenderCallbacks.isEmpty()) {
               profiler.popPush("iris_shadow_callbacks");
               this.pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
               ShadowRenderCallbacks.invoke(MODELVIEW, PROJECTION, cameraX, cameraY, cameraZ, CapturedRenderingState.INSTANCE.getTickDelta());
               this.pipeline.setPhase(WorldRenderingPhase.NONE);
            }

            this.pipeline.setPhase(WorldRenderingPhase.ENTITIES);
            GlStateManager._viewport(0, 0, this.resolution, this.resolution);
            profiler.popPush("entities");
            float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();
            boolean hasEntityFrustum = false;
            if (this.entityShadowDistanceMultiplier != 1.0F && !(this.entityShadowDistanceMultiplier < 0.0F)) {
               hasEntityFrustum = true;
               this.entityFrustumHolder = this.createShadowFrustum(
                  this.renderDistanceMultiplier * this.entityShadowDistanceMultiplier, this.entityFrustumHolder
               );
            } else {
               this.entityFrustumHolder
                  .setInfo(this.terrainFrustumHolder.getFrustum(), this.terrainFrustumHolder.getDistanceInfo(), this.terrainFrustumHolder.getCullingInfo());
            }

            Frustum entityShadowFrustum = this.entityFrustumHolder.getFrustum();
            entityShadowFrustum.prepare(cameraX, cameraY, cameraZ);
            this.levelRenderState.reset();
            if (this.shouldRenderEntities) {
               this.extractVisibleEntities(
                  playerCamera, this.entityFrustumHolder.getFrustum(), Minecraft.getInstance().getDeltaTracker(), this.levelRenderState
               );
            } else if (this.shouldRenderPlayer) {
               Player player = Minecraft.getInstance().player;
               float g = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
               if (!player.isSpectator() && !player.isInvisible()) {
                  this.levelRenderState.entityRenderStates.add(Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(player, g));
               }

               if (player.getVehicle() != null) {
                  this.levelRenderState.entityRenderStates.add(Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(player.getVehicle(), g));
               }
            }

            EntityRenderDispatcher dispatcher = levelRenderer.getEntityRenderDispatcher();
            RenderSystem.getModelViewStack().identity();
            this.renderedShadowEntities = this.renderEntities(levelRenderer, dispatcher, modelView, tickDelta, entityShadowFrustum, cameraX, cameraY, cameraZ);
            profiler.popPush("build blockentities");
            if (this.shouldRenderBlockEntities || this.shouldRenderLightBlockEntities) {
               this.extractVisibleBlockEntities(
                  sodiumWorldRenderer, tickDelta, playerCamera, this.levelRenderState, !this.shouldRenderBlockEntities && this.shouldRenderLightBlockEntities
               );
            }

            this.renderedShadowBlockEntities = this.renderBlockEntities(levelRenderer, modelView, this.submitNodeStorage, this.levelRenderState, playerCamera);
            profiler.popPush("draw entities");
            PreparedFrame frame = this.featureRenderDispatcher.prepareFrame(this.submitNodeStorage);
            RenderPass renderPass = RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(
                  () -> "Terrain",
                  Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(),
                  Optional.empty(),
                  Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView(),
                  OptionalDouble.empty()
               );

            try {
               FeatureRenderDispatcher.renderAllFeatures(renderPass, frame);
            } catch (Throwable var46) {
               if (renderPass != null) {
                  try {
                     renderPass.close();
                  } catch (Throwable var43) {
                     var46.addSuppressed(var43);
                  }
               }

               throw var46;
            }

            if (renderPass != null) {
               renderPass.close();
            }

            frame.close();
            this.buffers.endFrame();
            this.copyPreTranslucentDepth(levelRenderer);
            RenderSystem.getModelViewStack().set(MODELVIEW);
            profiler.popPush("translucent terrain");
            this.pipeline.setPhase(WorldRenderingPhase.NONE);
            if (this.shouldRenderTranslucent) {
               this.pipeline.setPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
               renderPass = RenderSystem.getDevice()
                  .createCommandEncoder()
                  .createRenderPass(
                     () -> "Terrain",
                     Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(),
                     Optional.empty(),
                     Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView(),
                     OptionalDouble.empty()
                  );

               try {
                  sections.renderGroup(ChunkSectionLayerGroup.TRANSLUCENT, renderPass, theSampler, atlas, false);
               } catch (Throwable var45) {
                  if (renderPass != null) {
                     try {
                        renderPass.close();
                     } catch (Throwable var42) {
                        var45.addSuppressed(var42);
                     }
                  }

                  throw var45;
               }

               if (renderPass != null) {
                  renderPass.close();
               }

               this.pipeline.setPhase(WorldRenderingPhase.NONE);
            }
         } finally {
            if (sodiumWorldRenderer instanceof ShadowRenderListAccess shadowRenderListAccess) {
               shadowRenderListAccess.iris$endShadowRenderListScope();
            }
         }

         IrisRenderSystem.restorePlayerProjection();
         this.debugStringTerrain = Minecraft.getInstance().levelExtractor.sectionStatistics();
         profiler.popPush("generate mipmaps");
         this.generateMipmaps();
         profiler.popPush("restore gl state");
         GlStateManager._enableCull();
         ((LevelRendererExtension)levelRenderer).sodium$setMatrices(playerMatrices);
         GlStateManager._viewport(0, 0, client.gameRenderer.mainRenderTarget().width, client.gameRenderer.mainRenderTarget().height);
         if (levelRenderer instanceof CullingDataCache) {
            ((CullingDataCache)levelRenderer).restoreState();
         }

         if (sodiumWorldRenderer instanceof ShadowRenderListAccess shadowRenderListAccess) {
            shadowRenderListAccess.iris$endShadowRenderListScope();
         }

         this.pipeline.removePhaseIfNeeded();
         GLDebug.pushGroup(901, "shadowcomp");
         this.compositeRenderer.renderAll();
         GLDebug.popGroup();
         levelRenderer.setRenderBuffers(playerBuffers);
         visibleBlockEntities = null;
         ACTIVE = false;
         this.levelRenderState.cameraRenderState.viewRotationMatrix = cameraViewRotation;
         this.levelRenderState.cameraRenderState.projectionMatrix = cameraProjection;
         RenderSystem.getModelViewStack().popMatrix();
         profiler.pop();
         profiler.popPush("updatechunks");
      }
   }

   private int renderBlockEntities(
      LevelRendererAccessor levelRenderer, PoseStack modelView, SubmitNodeStorage submitNodeStorage, LevelRenderState levelRenderState, Camera camera
   ) {
      Vec3 vec3 = camera.position();
      PoseStack poseStack = modelView;
      double d = vec3.x();
      double e = vec3.y();
      double f = vec3.z();
      int i = 0;

      for (BlockEntityRenderState blockEntityRenderState : levelRenderState.blockEntityRenderStates) {
         BlockPos blockPos = blockEntityRenderState.blockPos;
         poseStack.pushPose();
         poseStack.translate(blockPos.getX() - d, blockPos.getY() - e, blockPos.getZ() - f);
         Minecraft.getInstance()
            .getBlockEntityRenderDispatcher()
            .submit(blockEntityRenderState, poseStack, submitNodeStorage, levelRenderState.cameraRenderState);
         poseStack.popPose();
         i++;
      }

      return i;
   }

   private void extractVisibleBlockEntities(
      SodiumWorldRenderer worldRenderer, float tickDelta, Camera camera, LevelRenderState levelRenderState, boolean lightsOnly
   ) {
      worldRenderer.extractBlockEntities(camera, tickDelta, Minecraft.getInstance().level.destructionProgress(), levelRenderState);
      if (lightsOnly) {
         Iterator<BlockEntityRenderState> state = levelRenderState.blockEntityRenderStates.iterator();

         while (state.hasNext()) {
            BlockEntityRenderState blockEntityRenderState = state.next();
            if (blockEntityRenderState.blockState.getLightEmission() == 0) {
               state.remove();
            }
         }
      }
   }

   private int renderEntities(
      LevelRendererAccessor levelRenderer,
      EntityRenderDispatcher dispatcher,
      PoseStack modelView,
      float tickDelta,
      Frustum frustum,
      double cameraX,
      double cameraY,
      double cameraZ
   ) {
      Profiler.get().push("cull");

      for (EntityRenderState entityRenderState : this.levelRenderState.entityRenderStates) {
         Minecraft.getInstance()
            .getEntityRenderDispatcher()
            .submit(
               entityRenderState,
               this.levelRenderState.cameraRenderState,
               entityRenderState.x - cameraX,
               entityRenderState.y - cameraY,
               entityRenderState.z - cameraZ,
               modelView,
               this.submitNodeStorage
            );
      }

      Profiler.get().pop();
      return this.levelRenderState.entityRenderStates.size();
   }

   private void extractVisibleEntities(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState levelRenderState) {
      Vec3 vec3 = camera.position();
      double d = vec3.x();
      double e = vec3.y();
      double f = vec3.z();
      TickRateManager tickRateManager = Minecraft.getInstance().level.tickRateManager();
      Entity.setViewScale(
         Mth.clamp(Minecraft.getInstance().options.getEffectiveRenderDistance() / 8.0, 1.0, 2.5)
            * (Double)Minecraft.getInstance().options.entityDistanceScaling().get()
      );

      for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
         if (!(entity instanceof AbstractClientPlayer acp && acp.isSpectator())
            && (
               Minecraft.getInstance().getEntityRenderDispatcher().shouldRender(entity, frustum, d, e, f, levelRenderState.worldPartialTicks)
                  || entity.hasIndirectPassenger(Minecraft.getInstance().player)
            )) {
            BlockPos blockPos = entity.blockPosition();
            if (Minecraft.getInstance().level.isOutsideBuildHeight(blockPos.getY())
               || Minecraft.getInstance().levelRenderer.isSectionCompiledAndVisible(blockPos, 0L)) {
               if (entity.tickCount == 0) {
                  entity.xOld = entity.getX();
                  entity.yOld = entity.getY();
                  entity.zOld = entity.getZ();
               }

               float g = deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity));
               EntityRenderState entityRenderState = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(entity, g);
               levelRenderState.entityRenderStates.add(entityRenderState);
            }
         }
      }
   }

   private int renderPlayerEntity(
      LevelRendererAccessor levelRenderer,
      EntityRenderDispatcher dispatcher,
      PoseStack modelView,
      float tickDelta,
      Frustum frustum,
      double cameraX,
      double cameraY,
      double cameraZ
   ) {
      Profiler.get().push("cull");
      Entity player = Minecraft.getInstance().player;
      int shadowEntities = 0;
      if (dispatcher.shouldRender(player, frustum, cameraX, cameraY, cameraZ, tickDelta) && !player.isSpectator()) {
         Profiler.get().popPush("build geometry");
         if (!player.getPassengers().isEmpty()) {
            for (int i = 0; i < player.getPassengers().size(); i++) {
               if (!Minecraft.getInstance().level.tickRateManager().isEntityFrozen((Entity)player.getPassengers().get(i))) {
                  CapturedRenderingState.INSTANCE.getRealTickDelta();
               }

               shadowEntities++;
            }
         }

         if (player.getVehicle() != null) {
            if (!Minecraft.getInstance().level.tickRateManager().isEntityFrozen(player.getVehicle())) {
               CapturedRenderingState.INSTANCE.getRealTickDelta();
            }

            shadowEntities++;
         }

         if (!Minecraft.getInstance().level.tickRateManager().isEntityFrozen(player)) {
            CapturedRenderingState.INSTANCE.getRealTickDelta();
         }

         shadowEntities++;
         Profiler.get().pop();
         return shadowEntities;
      } else {
         Profiler.get().pop();
         return 0;
      }
   }

   private void copyPreTranslucentDepth(LevelRendererAccessor levelRenderer) {
      Profiler.get().popPush("translucent depth copy");
      this.targets.copyPreTranslucentDepth();
   }

   public void addDebugText(DebugScreenDisplayer messages) {
      if (IrisVideoSettings.getOverriddenShadowDistance(IrisVideoSettings.shadowDistance) == 0) {
         messages.addLine("[Iris] Shadow Maps: off, shadow distance 0");
      } else {
         if (Iris.getIrisConfig().areDebugOptionsEnabled()) {
            messages.addLine("[Iris] Shadow Maps: " + this.debugStringOverall);
            messages.addLine(
               "[Iris] Shadow Distance Terrain: " + this.terrainFrustumHolder.getDistanceInfo() + " Entity: " + this.entityFrustumHolder.getDistanceInfo()
            );
            messages.addLine(
               "[Iris] Shadow Culling Terrain: " + this.terrainFrustumHolder.getCullingInfo() + " Entity: " + this.entityFrustumHolder.getCullingInfo()
            );
            messages.addLine("[Iris] Shadow Projection: " + this.getProjectionInfo());
            messages.addLine(
               "[Iris] Shadow Terrain: "
                  + this.debugStringTerrain
                  + (this.shouldRenderTerrain ? "" : " (no terrain) ")
                  + (this.shouldRenderTranslucent ? "" : "(no translucent)")
            );
            messages.addLine("[Iris] Shadow Entities: " + this.getEntitiesDebugString());
            messages.addLine("[Iris] Shadow Block Entities: " + this.getBlockEntitiesDebugString());
         } else {
            messages.addLine("[Iris] Shadow info: " + this.debugStringTerrain);
            messages.addLine("[Iris] E: " + this.renderedShadowEntities);
            messages.addLine("[Iris] BE: " + this.renderedShadowBlockEntities);
         }
      }
   }

   private String getProjectionInfo() {
      return "Near: " + this.nearPlane + " Far: " + this.farPlane + " distance " + this.halfPlaneLength;
   }

   private String getEntitiesDebugString() {
      return !this.shouldRenderEntities && !this.shouldRenderPlayer
         ? "disabled by pack"
         : this.renderedShadowEntities + "/" + (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getEntityCount());
   }

   private String getBlockEntitiesDebugString() {
      return !this.shouldRenderBlockEntities && !this.shouldRenderLightBlockEntities ? "disabled by pack" : this.renderedShadowBlockEntities + "";
   }

   public void destroy() {
   }

   private record MipmapPass(int texture, int targetFilteringMode) {
   }
}
