package net.irisshaders.iris.compat.dh;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.seibel.distanthorizons.api.DhApi.Delayed;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import java.io.IOException;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.gl.texture.DepthCopyStrategy;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.CloudSetting;
import net.irisshaders.iris.targets.Blaze3dRenderTargetExt;
import net.irisshaders.iris.targets.DepthTexture;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;

public class DHCompatInternal {
   public static final DHCompatInternal SHADERLESS = new DHCompatInternal(null, false);
   static boolean dhEnabled;
   private static int guiScale = -1;
   private final IrisRenderingPipeline pipeline;
   public boolean shouldOverrideShadow;
   public boolean shouldOverride;
   private GlFramebuffer dhGenericFramebuffer;
   private IrisLodRenderProgram solidProgram;
   private IrisGenericRenderProgram genericShader;
   private IrisLodRenderProgram translucentProgram;
   private IrisLodRenderProgram shadowProgram;
   private GlFramebuffer dhTerrainFramebuffer;
   private DhFrameBufferWrapper dhTerrainFramebufferWrapper;
   private GlFramebuffer dhWaterFramebuffer;
   private GlFramebuffer dhShadowFramebuffer;
   private DhFrameBufferWrapper dhShadowFramebufferWrapper;
   private DepthTexture depthTexNoTranslucent;
   private boolean translucentDepthDirty;
   private int storedDepthTex = -1;
   private boolean incompatible = false;
   private int cachedVersion;

   public DHCompatInternal(IrisRenderingPipeline pipeline, boolean dhShadowEnabled) {
      this.pipeline = pipeline;
      if (pipeline != null && (Boolean)Delayed.configs.graphics().renderingEnabled().getValue()) {
         if (pipeline.getDHTerrainShader().isEmpty() && pipeline.getDHWaterShader().isEmpty()) {
            Iris.logger.warn("No DH shader found in this pack.");
            this.incompatible = true;
         } else {
            this.cachedVersion = ((Blaze3dRenderTargetExt)Minecraft.getInstance().gameRenderer.mainRenderTarget()).iris$getDepthBufferVersion();
            this.createDepthTex(Minecraft.getInstance().gameRenderer.mainRenderTarget().width, Minecraft.getInstance().gameRenderer.mainRenderTarget().height);
            this.translucentDepthDirty = true;
            ProgramSource terrain = pipeline.getDHTerrainShader().get();
            this.solidProgram = IrisLodRenderProgram.createProgram(terrain.getName(), false, false, terrain, pipeline.getCustomUniforms(), pipeline);
            ProgramSource generic = pipeline.getDHGenericShader().get();
            this.genericShader = IrisGenericRenderProgram.createProgram(generic.getName() + "_g", false, false, generic, pipeline.getCustomUniforms(), pipeline);
            this.dhGenericFramebuffer = pipeline.createDHFramebuffer(generic, false);
            if (pipeline.getDHWaterShader().isPresent()) {
               ProgramSource water = pipeline.getDHWaterShader().get();
               this.translucentProgram = IrisLodRenderProgram.createProgram(water.getName(), false, true, water, pipeline.getCustomUniforms(), pipeline);
               this.dhWaterFramebuffer = pipeline.createDHFramebuffer(water, true);
            }

            if (pipeline.getDHShadowShader().isPresent() && dhShadowEnabled) {
               ProgramSource shadow = pipeline.getDHShadowShader().get();
               this.shadowProgram = IrisLodRenderProgram.createProgram(shadow.getName(), true, false, shadow, pipeline.getCustomUniforms(), pipeline);
               if (pipeline.hasShadowRenderTargets()) {
                  this.dhShadowFramebuffer = pipeline.createDHFramebufferShadow(shadow);
                  this.dhShadowFramebufferWrapper = new DhFrameBufferWrapper(this.dhShadowFramebuffer);
               }

               this.shouldOverrideShadow = true;
            } else {
               this.shouldOverrideShadow = false;
            }

            this.dhTerrainFramebuffer = pipeline.createDHFramebuffer(terrain, false);
            this.dhTerrainFramebufferWrapper = new DhFrameBufferWrapper(this.dhTerrainFramebuffer);
            if (this.translucentProgram == null) {
               this.translucentProgram = this.solidProgram;
            }

            this.shouldOverride = true;
         }
      }
   }

   public static int getDhBlockRenderDistance() {
      return Delayed.configs != null && dhEnabled
         ? (Integer)Delayed.configs.graphics().chunkRenderDistance().getValue() * 16
         : Minecraft.getInstance().options.getEffectiveRenderDistance();
   }

   public static int getRenderDistance() {
      return getDhBlockRenderDistance();
   }

   public static float getFarPlane() {
      if (Delayed.configs == null) {
         return 0.0F;
      }

      int lodChunkDist = (Integer)Delayed.configs.graphics().chunkRenderDistance().getValue();
      int lodBlockDist = lodChunkDist * 16;
      return (float)((lodBlockDist + 512) * Math.sqrt(2.0));
   }

   public static float getNearPlane() {
      return Delayed.renderProxy == null ? 0.0F : Delayed.renderProxy.getNearClipPlaneDistanceInBlocks(CapturedRenderingState.INSTANCE.getRealTickDelta());
   }

   public static boolean checkFrame() {
      if (guiScale == -1) {
         guiScale = (Integer)Minecraft.getInstance().options.guiScale().get();
      }

      if (Delayed.configs == null) {
         return dhEnabled;
      }

      if ((
            dhEnabled != (Boolean)Delayed.configs.graphics().renderingEnabled().getValue()
               || guiScale != (Integer)Minecraft.getInstance().options.guiScale().get()
         )
         && Iris.isPackInUseQuick()) {
         guiScale = (Integer)Minecraft.getInstance().options.guiScale().get();
         dhEnabled = (Boolean)Delayed.configs.graphics().renderingEnabled().getValue();

         try {
            Iris.reload();
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }

      return dhEnabled;
   }

   public boolean incompatiblePack() {
      return this.incompatible;
   }

   public void reconnectDHTextures(int depthTex) {
      if (((Blaze3dRenderTargetExt)Minecraft.getInstance().gameRenderer.mainRenderTarget()).iris$getDepthBufferVersion() != this.cachedVersion) {
         this.cachedVersion = ((Blaze3dRenderTargetExt)Minecraft.getInstance().gameRenderer.mainRenderTarget()).iris$getDepthBufferVersion();
         this.createDepthTex(Minecraft.getInstance().gameRenderer.mainRenderTarget().width, Minecraft.getInstance().gameRenderer.mainRenderTarget().height);
      }

      if (this.storedDepthTex != depthTex && this.dhTerrainFramebuffer != null) {
         this.storedDepthTex = depthTex;
         this.dhTerrainFramebuffer.addDepthAttachmentBypass(depthTex);
         if (this.dhWaterFramebuffer != null) {
            this.dhWaterFramebuffer.addDepthAttachmentBypass(depthTex);
         }

         if (this.dhGenericFramebuffer != null) {
            this.dhGenericFramebuffer.addDepthAttachmentBypass(depthTex);
         }
      }
   }

   public void createDepthTex(int width, int height) {
      if (this.depthTexNoTranslucent != null) {
         this.depthTexNoTranslucent.destroy();
         this.depthTexNoTranslucent = null;
      }

      this.translucentDepthDirty = true;
      this.depthTexNoTranslucent = new DepthTexture("DH depth tex", width, height, DepthBufferFormat.DEPTH32F);
   }

   public void clear() {
      if (this.solidProgram != null) {
         this.solidProgram.free();
         this.solidProgram = null;
      }

      if (this.translucentProgram != null) {
         this.translucentProgram.free();
         this.translucentProgram = null;
      }

      if (this.shadowProgram != null) {
         this.shadowProgram.free();
         this.shadowProgram = null;
      }

      this.shouldOverrideShadow = false;
      this.shouldOverride = false;
      this.dhTerrainFramebuffer = null;
      this.dhWaterFramebuffer = null;
      this.dhShadowFramebuffer = null;
      this.storedDepthTex = -1;
      this.translucentDepthDirty = true;
      OverrideInjector.INSTANCE.unbind(IDhApiFramebuffer.class, this.dhTerrainFramebufferWrapper);
      OverrideInjector.INSTANCE.unbind(IDhApiGenericObjectShaderProgram.class, this.genericShader);
      OverrideInjector.INSTANCE.unbind(IDhApiFramebuffer.class, this.dhShadowFramebufferWrapper);
      this.dhTerrainFramebufferWrapper = null;
      this.dhShadowFramebufferWrapper = null;
   }

   public void setModelPos(DhApiVec3f modelPos) {
      this.solidProgram.bind();
      this.solidProgram.setModelPos(modelPos);
      this.translucentProgram.bind();
      this.translucentProgram.setModelPos(modelPos);
      this.solidProgram.bind();
   }

   public IrisLodRenderProgram getSolidShader() {
      return this.solidProgram;
   }

   public GlFramebuffer getSolidFB() {
      return this.dhTerrainFramebuffer;
   }

   public DhFrameBufferWrapper getSolidFBWrapper() {
      return this.dhTerrainFramebufferWrapper;
   }

   public IrisLodRenderProgram getShadowShader() {
      return this.shadowProgram;
   }

   public GlFramebuffer getShadowFB() {
      return this.dhShadowFramebuffer;
   }

   public DhFrameBufferWrapper getShadowFBWrapper() {
      return this.dhShadowFramebufferWrapper;
   }

   public IrisLodRenderProgram getTranslucentShader() {
      return this.translucentProgram == null ? this.solidProgram : this.translucentProgram;
   }

   public int getStoredDepthTex() {
      return this.storedDepthTex;
   }

   public void copyTranslucents(int width, int height) {
      if (this.translucentDepthDirty) {
         this.translucentDepthDirty = false;
         GlStateManager._bindTexture(this.depthTexNoTranslucent.getTextureId());
         this.dhTerrainFramebuffer.bindAsReadBuffer();
         IrisRenderSystem.copyTexImage2D(3553, 0, DepthBufferFormat.DEPTH32F.getGlInternalFormat(), 0, 0, width, height, 0);
      } else {
         DepthCopyStrategy.fastest(false).copy(this.dhTerrainFramebuffer, this.storedDepthTex, null, this.depthTexNoTranslucent.getTextureId(), width, height);
      }
   }

   public GlFramebuffer getTranslucentFB() {
      return this.dhWaterFramebuffer;
   }

   public GlFramebuffer getGenericFB() {
      return this.dhGenericFramebuffer;
   }

   public int getDepthTexNoTranslucent() {
      return this.depthTexNoTranslucent == null ? 0 : this.depthTexNoTranslucent.getTextureId();
   }

   public IDhApiGenericObjectShaderProgram getGenericShader() {
      return this.genericShader;
   }

   public boolean avoidRenderingClouds() {
      return this.pipeline != null
         && (
            this.pipeline.getDHCloudSetting() == CloudSetting.OFF
               || this.pipeline.getDHCloudSetting() == CloudSetting.DEFAULT && this.pipeline.getCloudSetting() == CloudSetting.OFF
         );
   }
}
