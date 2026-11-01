package net.irisshaders.iris.targets;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.gl.texture.DepthCopyStrategy;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import org.joml.Vector2i;

public class RenderTargets {
   private final RenderTarget[] targets;
   private GpuTexture noTranslucents;
   private GpuTexture noHand;
   private final GlFramebuffer depthSourceFb;
   private final GlFramebuffer noTranslucentsDestFb;
   private final GlFramebuffer noHandDestFb;
   private final List<GlFramebuffer> ownedFramebuffers;
   private final Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> targetSettingsMap;
   private final PackDirectives packDirectives;
   private GpuTexture currentDepthTexture;
   private DepthBufferFormat currentDepthFormat;
   private DepthCopyStrategy copyStrategy;
   private int cachedWidth;
   private int cachedHeight;
   private boolean fullClearRequired;
   private boolean translucentDepthDirty;
   private boolean handDepthDirty;
   private int cachedDepthBufferVersion;
   private boolean destroyed;

   public RenderTargets(
      int width,
      int height,
      GpuTexture depthTexture,
      int depthBufferVersion,
      DepthBufferFormat depthFormat,
      Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargets,
      PackDirectives packDirectives
   ) {
      this.targets = new RenderTarget[renderTargets.size()];
      this.targetSettingsMap = renderTargets;
      this.packDirectives = packDirectives;
      this.currentDepthTexture = depthTexture;
      this.currentDepthFormat = depthFormat;
      this.copyStrategy = DepthCopyStrategy.fastest(this.currentDepthFormat.isCombinedStencil());
      this.cachedWidth = width;
      this.cachedHeight = height;
      this.cachedDepthBufferVersion = depthBufferVersion;
      this.ownedFramebuffers = new ArrayList<>();
      this.fullClearRequired = true;
      this.depthSourceFb = this.createFramebufferWritingToMain(new int[]{0});
      GpuFormat mojangDepthFormat = depthFormat.toMojang();
      this.noTranslucents = RenderSystem.getDevice().createTexture("Depth / Opaque", 5, mojangDepthFormat, width, height, 1, 1);
      this.noHand = RenderSystem.getDevice().createTexture("Depth / Before Hand", 5, mojangDepthFormat, width, height, 1, 1);
      this.noTranslucentsDestFb = this.createFramebufferWritingToMain(new int[]{0});
      this.noTranslucentsDestFb.addDepthAttachment(this.noTranslucents);
      this.noHandDestFb = this.createFramebufferWritingToMain(new int[]{0});
      this.noHandDestFb.addDepthAttachment(this.noHand);
      this.translucentDepthDirty = true;
      this.handDepthDirty = true;
   }

   public void destroy() {
      this.destroyed = true;

      for (GlFramebuffer owned : this.ownedFramebuffers) {
         owned.destroy();
      }

      for (RenderTarget target : this.targets) {
         if (target != null) {
            target.destroy();
         }
      }

      this.noTranslucents.close();
      this.noHand.close();
   }

   public int getRenderTargetCount() {
      return this.targets.length;
   }

   public RenderTarget get(int index) {
      if (this.destroyed) {
         throw new IllegalStateException("Tried to use destroyed RenderTargets");
      } else {
         return this.targets[index] == null ? null : this.targets[index];
      }
   }

   public RenderTarget getOrCreate(int index) {
      if (this.destroyed) {
         throw new IllegalStateException("Tried to use destroyed RenderTargets");
      }

      if (this.targets[index] != null) {
         return this.targets[index];
      }

      this.create(index);
      return this.targets[index];
   }

   private void create(int index) {
      PackRenderTargetDirectives.RenderTargetSettings settings = this.targetSettingsMap.get(index);
      Vector2i dimensions = this.packDirectives.getTextureScaleOverride(index, this.cachedWidth, this.cachedHeight);
      this.targets[index] = RenderTarget.builder()
         .setDimensions(dimensions.x, dimensions.y)
         .setName("colortex" + index)
         .setInternalFormat(settings.getInternalFormat())
         .setPixelFormat(settings.getInternalFormat().getPixelFormat())
         .build();
   }

   public GpuTexture getDepthTexture() {
      return this.currentDepthTexture;
   }

   public GpuTexture getDepthTextureNoTranslucents() {
      if (this.destroyed) {
         throw new IllegalStateException("Tried to use destroyed RenderTargets");
      } else {
         return this.noTranslucents;
      }
   }

   public GpuTexture getDepthTextureNoHand() {
      return this.noHand;
   }

   public boolean resizeIfNeeded(
      int newDepthBufferVersion, GpuTexture newDepthTextureId, int newWidth, int newHeight, DepthBufferFormat newDepthFormat, PackDirectives packDirectives
   ) {
      boolean recreateDepth = false;
      if (this.cachedDepthBufferVersion != newDepthBufferVersion) {
         recreateDepth = true;
         this.currentDepthTexture = newDepthTextureId;
         this.cachedDepthBufferVersion = newDepthBufferVersion;
      }

      boolean sizeChanged = newWidth != this.cachedWidth || newHeight != this.cachedHeight;
      boolean depthFormatChanged = newDepthFormat != this.currentDepthFormat;
      if (depthFormatChanged) {
         this.currentDepthFormat = newDepthFormat;
         this.copyStrategy = DepthCopyStrategy.fastest(this.currentDepthFormat.isCombinedStencil());
      }

      if (depthFormatChanged || sizeChanged) {
         this.noTranslucents.close();
         this.noHand.close();
         this.noTranslucents = RenderSystem.getDevice().createTexture("Depth / Opaque", 5, newDepthTextureId.getFormat(), newWidth, newHeight, 1, 1);
         this.noHand = RenderSystem.getDevice().createTexture("Depth / Before Hand", 5, newDepthTextureId.getFormat(), newWidth, newHeight, 1, 1);
         this.noTranslucentsDestFb.addDepthAttachment(this.noTranslucents);
         this.noHandDestFb.addDepthAttachment(this.noHand);
         this.translucentDepthDirty = true;
         this.handDepthDirty = true;
         recreateDepth = true;
      }

      if (recreateDepth) {
         for (GlFramebuffer framebuffer : this.ownedFramebuffers) {
            if (framebuffer.hasDepthAttachment()) {
               framebuffer.addDepthAttachment(newDepthTextureId);
            }
         }
      }

      if (sizeChanged) {
         this.cachedWidth = newWidth;
         this.cachedHeight = newHeight;

         for (int i = 0; i < this.targets.length; i++) {
            if (this.targets[i] != null) {
               this.targets[i].resize(packDirectives.getTextureScaleOverride(i, newWidth, newHeight));
            }
         }

         this.fullClearRequired = true;
      }

      return sizeChanged;
   }

   public void copyPreTranslucentDepth() {
      if (this.translucentDepthDirty) {
         this.translucentDepthDirty = false;
         GlStateManager._bindTexture(this.noTranslucents.iris$getGlId());
         this.depthSourceFb.bindAsReadBuffer();
         IrisRenderSystem.copyTexImage2D(3553, 0, this.currentDepthFormat.getGlInternalFormat(), 0, 0, this.cachedWidth, this.cachedHeight, 0);
      } else {
         this.copyStrategy
            .copy(
               this.depthSourceFb,
               this.getDepthTexture().iris$getGlId(),
               this.noTranslucentsDestFb,
               this.noTranslucents.iris$getGlId(),
               this.getCurrentWidth(),
               this.getCurrentHeight()
            );
      }
   }

   public void copyPreHandDepth() {
      if (this.handDepthDirty) {
         this.handDepthDirty = false;
         GlStateManager._bindTexture(this.noHand.iris$getGlId());
         this.depthSourceFb.bindAsReadBuffer();
         IrisRenderSystem.copyTexImage2D(3553, 0, this.currentDepthFormat.getGlInternalFormat(), 0, 0, this.cachedWidth, this.cachedHeight, 0);
      } else {
         this.copyStrategy
            .copy(
               this.depthSourceFb,
               this.getDepthTexture().iris$getGlId(),
               this.noHandDestFb,
               this.noHand.iris$getGlId(),
               this.getCurrentWidth(),
               this.getCurrentHeight()
            );
      }
   }

   public boolean isFullClearRequired() {
      return this.fullClearRequired;
   }

   public void onFullClear() {
      this.fullClearRequired = false;
   }

   public GlFramebuffer createFramebufferWritingToMain(int[] drawBuffers) {
      return this.createFullFramebuffer(false, drawBuffers);
   }

   public GlFramebuffer createFramebufferWritingToAlt(int[] drawBuffers) {
      return this.createFullFramebuffer(true, drawBuffers);
   }

   public GlFramebuffer createClearFramebuffer(boolean alt, int[] clearBuffers) {
      ImmutableSet<Integer> stageWritesToMain = ImmutableSet.of();
      if (!alt) {
         stageWritesToMain = this.invert(ImmutableSet.of(), clearBuffers);
      }

      return this.createColorFramebuffer(stageWritesToMain, clearBuffers);
   }

   private ImmutableSet<Integer> invert(ImmutableSet<Integer> base, int[] relevant) {
      Builder<Integer> inverted = ImmutableSet.builder();

      for (int i : relevant) {
         if (!base.contains(i)) {
            inverted.add(i);
         }
      }

      return inverted.build();
   }

   private GlFramebuffer createEmptyFramebuffer() {
      GlFramebuffer framebuffer = new GlFramebuffer();
      this.ownedFramebuffers.add(framebuffer);
      framebuffer.addDepthAttachment(this.currentDepthTexture);
      framebuffer.addColorAttachment(0, this.getOrCreate(0).getMainTexture());
      framebuffer.noDrawBuffers();
      return framebuffer;
   }

   public GlFramebuffer createDHFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
      if (drawBuffers.length == 0) {
         return this.createEmptyFramebuffer();
      }

      ImmutableSet<Integer> stageWritesToMain = this.invert(stageWritesToAlt, drawBuffers);
      return this.createColorFramebuffer(stageWritesToMain, drawBuffers);
   }

   public GlFramebuffer createGbufferFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
      if (drawBuffers.length == 0) {
         return this.createEmptyFramebuffer();
      }

      ImmutableSet<Integer> stageWritesToMain = this.invert(stageWritesToAlt, drawBuffers);
      GlFramebuffer framebuffer = this.createColorFramebuffer(stageWritesToMain, drawBuffers);
      framebuffer.addDepthAttachment(this.currentDepthTexture);
      return framebuffer;
   }

   private GlFramebuffer createFullFramebuffer(boolean clearsAlt, int[] drawBuffers) {
      if (drawBuffers.length == 0) {
         return this.createEmptyFramebuffer();
      }

      ImmutableSet<Integer> stageWritesToMain = ImmutableSet.of();
      if (!clearsAlt) {
         stageWritesToMain = this.invert(ImmutableSet.of(), drawBuffers);
      }

      return this.createColorFramebufferWithDepth(stageWritesToMain, drawBuffers);
   }

   public GlFramebuffer createColorFramebufferWithDepth(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
      GlFramebuffer framebuffer = this.createColorFramebuffer(stageWritesToMain, drawBuffers);
      framebuffer.addDepthAttachment(this.currentDepthTexture);
      return framebuffer;
   }

   public GlFramebuffer createColorFramebuffer(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
      if (drawBuffers.length == 0) {
         throw new IllegalArgumentException("Framebuffer must have at least one color buffer");
      }

      GlFramebuffer framebuffer = new GlFramebuffer();
      this.ownedFramebuffers.add(framebuffer);
      int[] actualDrawBuffers = new int[drawBuffers.length];

      for (int i = 0; i < drawBuffers.length; i++) {
         actualDrawBuffers[i] = i;
         if (drawBuffers[i] >= this.getRenderTargetCount()) {
            framebuffer.destroy();
            this.ownedFramebuffers.remove(framebuffer);
            throw new IllegalStateException(
               "Render target with index " + drawBuffers[i] + " is not supported, only " + this.getRenderTargetCount() + " render targets are supported."
            );
         }

         RenderTarget target = this.getOrCreate(drawBuffers[i]);
         int textureId = stageWritesToMain.contains(drawBuffers[i]) ? target.getMainTexture() : target.getAltTexture();
         framebuffer.addColorAttachment(i, textureId);
      }

      framebuffer.drawBuffers(actualDrawBuffers);
      framebuffer.readBuffer(0);
      int status = framebuffer.getStatus();
      if (status != 36053) {
         throw new IllegalStateException(
            "Unexpected error while creating framebuffer: Draw buffers " + Arrays.toString(actualDrawBuffers) + " Status: " + status
         );
      } else {
         return framebuffer;
      }
   }

   public void destroyFramebuffer(GlFramebuffer framebuffer) {
      framebuffer.destroy();
      this.ownedFramebuffers.remove(framebuffer);
   }

   public int getCurrentWidth() {
      return this.cachedWidth;
   }

   public int getCurrentHeight() {
      return this.cachedHeight;
   }

   public void createIfUnsure(int index) {
      if (this.targets[index] == null) {
         this.create(index);
      }
   }
}
