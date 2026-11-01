package net.irisshaders.iris.shadows;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.texture.DepthCopyStrategy;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.properties.PackShadowDirectives;
import net.irisshaders.iris.targets.RenderTarget;

public class ShadowRenderTargets {
   private final RenderTarget[] targets;
   private final PackShadowDirectives shadowDirectives;
   private final GpuTexture mainDepth;
   private final GpuTexture noTranslucents;
   private final GlFramebuffer depthSourceFb;
   private final GlFramebuffer noTranslucentsDestFb;
   private final boolean[] flipped;
   private final List<GlFramebuffer> ownedFramebuffers;
   private final int resolution;
   private final boolean[] hardwareFiltered;
   private final boolean[] mipped;
   private final boolean[] linearFiltered;
   private final InternalTextureFormat[] formats;
   private final IntList buffersToBeCleared;
   private final int size;
   private boolean fullClearRequired;
   private boolean translucentDepthDirty;
   private static final double LN_OF_2 = Math.log(2.0);

   public ShadowRenderTargets(WorldRenderingPipeline pipeline, int resolution, PackShadowDirectives shadowDirectives) {
      this.shadowDirectives = shadowDirectives;
      this.size = pipeline.hasFeature(FeatureFlags.HIGHER_SHADOWCOLOR) ? 8 : 2;
      this.targets = new RenderTarget[this.size];
      this.formats = new InternalTextureFormat[this.size];
      this.flipped = new boolean[this.size];
      this.hardwareFiltered = new boolean[this.size];
      this.mipped = new boolean[this.size];
      this.linearFiltered = new boolean[this.size];
      this.buffersToBeCleared = new IntArrayList();
      this.ownedFramebuffers = new ArrayList<>();
      this.resolution = resolution;

      for (int i = 0; i < shadowDirectives.getDepthSamplingSettings().size(); i++) {
         this.hardwareFiltered[i] = ((PackShadowDirectives.DepthSamplingSettings)shadowDirectives.getDepthSamplingSettings().get(i)).getHardwareFiltering();
         this.mipped[i] = ((PackShadowDirectives.DepthSamplingSettings)shadowDirectives.getDepthSamplingSettings().get(i)).getMipmap();
         this.linearFiltered[i] = !((PackShadowDirectives.DepthSamplingSettings)shadowDirectives.getDepthSamplingSettings().get(i)).getNearest();
      }

      this.mainDepth = RenderSystem.getDevice()
         .createTexture("Shadow Map", 15, GpuFormat.D32_FLOAT, resolution, resolution, 1, this.mipped[0] ? log2(resolution) : 1);
      this.noTranslucents = RenderSystem.getDevice()
         .createTexture("Shadow Map / Opaque", 13, GpuFormat.D32_FLOAT, resolution, resolution, 1, this.mipped[1] ? log2(resolution) : 1);
      this.fullClearRequired = true;
      this.depthSourceFb = this.createFramebufferWritingToMain(new int[]{0});
      this.noTranslucentsDestFb = this.createFramebufferWritingToMain(new int[]{0});
      this.noTranslucentsDestFb.addDepthAttachment(this.noTranslucents);
      this.translucentDepthDirty = true;
      boolean shouldRefresh = false;
   }

   public static int log2(int val) {
      return (int)Math.floor(Math.log(val) / LN_OF_2);
   }

   public void flip(int target) {
      this.flipped[target] = !this.flipped[target];
   }

   public boolean isFlipped(int target) {
      return this.flipped[target];
   }

   public void destroy() {
      for (GlFramebuffer owned : this.ownedFramebuffers) {
         owned.destroy();
      }

      for (RenderTarget target : this.targets) {
         if (target != null) {
            target.destroy();
         }
      }

      this.mainDepth.close();
      this.noTranslucents.close();
   }

   public int getRenderTargetCount() {
      return this.targets.length;
   }

   public RenderTarget get(int index) {
      return this.targets[index];
   }

   public RenderTarget getOrCreate(int index) {
      if (this.targets[index] != null) {
         return this.targets[index];
      }

      this.create(index);
      return this.targets[index];
   }

   private void create(int index) {
      if (index > this.size) {
         throw new IllegalStateException(
            "Tried to access buffer higher than allowed limit of "
               + this.size
               + "! If you're trying to use shadowcolor2-7, you need to activate it's feature flag!"
         );
      }

      PackShadowDirectives.SamplingSettings settings = (PackShadowDirectives.SamplingSettings)this.shadowDirectives
         .getColorSamplingSettings()
         .computeIfAbsent(index, i -> new PackShadowDirectives.SamplingSettings());
      this.targets[index] = RenderTarget.builder()
         .setDimensions(this.resolution, this.resolution)
         .setInternalFormat(settings.getFormat())
         .setName("shadowcolor" + index)
         .setPixelFormat(settings.getFormat().getPixelFormat())
         .build();
      this.formats[index] = settings.getFormat();
      if (settings.getClear()) {
         this.buffersToBeCleared.add(index);
      }

      if (settings.getClear()) {
         this.buffersToBeCleared.add(index);
      }

      this.fullClearRequired = true;
   }

   public void createIfEmpty(int index) {
      if (this.targets[index] == null) {
         this.create(index);
      }
   }

   public int getResolution() {
      return this.resolution;
   }

   public GpuTexture getDepthTexture() {
      return this.mainDepth;
   }

   public GpuTexture getDepthTextureNoTranslucents() {
      return this.noTranslucents;
   }

   public GlFramebuffer getDepthSourceFb() {
      return this.depthSourceFb;
   }

   public void copyPreTranslucentDepth() {
      if (this.translucentDepthDirty) {
         this.translucentDepthDirty = false;
         IrisRenderSystem.blitFramebuffer(
            this.depthSourceFb.getId(),
            this.noTranslucentsDestFb.getId(),
            0,
            0,
            this.resolution,
            this.resolution,
            0,
            0,
            this.resolution,
            this.resolution,
            256,
            9728
         );
      } else {
         DepthCopyStrategy.fastest(false)
            .copy(
               this.depthSourceFb,
               this.mainDepth.iris$getGlId(),
               this.noTranslucentsDestFb,
               this.noTranslucents.iris$getGlId(),
               this.resolution,
               this.resolution
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
      framebuffer.addDepthAttachment(this.mainDepth);
      framebuffer.addColorAttachment(0, this.get(0).getMainTexture());
      framebuffer.noDrawBuffers();
      return framebuffer;
   }

   public GlFramebuffer createDHFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
      if (drawBuffers.length == 0) {
         return this.createEmptyFramebuffer();
      }

      ImmutableSet<Integer> stageWritesToMain = this.invert(stageWritesToAlt, drawBuffers);
      GlFramebuffer framebuffer = this.createColorFramebuffer(stageWritesToMain, drawBuffers);
      framebuffer.addDepthAttachment(this.mainDepth);
      return framebuffer;
   }

   public GlFramebuffer createShadowFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
      if (drawBuffers.length == 0) {
         return this.createEmptyFramebuffer();
      }

      ImmutableSet<Integer> stageWritesToMain = this.invert(stageWritesToAlt, drawBuffers);
      GlFramebuffer framebuffer = this.createColorFramebuffer(stageWritesToMain, drawBuffers);
      framebuffer.addDepthAttachment(this.mainDepth);
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
      framebuffer.addDepthAttachment(this.mainDepth);
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
            this.ownedFramebuffers.remove(framebuffer);
            framebuffer.destroy();
            return this.createColorFramebuffer(stageWritesToMain, new int[]{0, 1});
         }

         RenderTarget target = this.getOrCreate(drawBuffers[i]);
         int textureId = stageWritesToMain.contains(drawBuffers[i]) ? target.getMainTexture() : target.getAltTexture();
         framebuffer.addColorAttachment(i, textureId);
      }

      framebuffer.drawBuffers(actualDrawBuffers);
      framebuffer.readBuffer(0);
      int status = framebuffer.getStatus();
      if (status != 36053) {
         throw new IllegalStateException("Unexpected error while creating framebuffer");
      } else {
         return framebuffer;
      }
   }

   public int getColorTextureId(int i) {
      return this.isFlipped(i) ? this.get(i).getAltTexture() : this.get(i).getMainTexture();
   }

   public boolean isHardwareFiltered(int i) {
      return this.hardwareFiltered[i];
   }

   public int getNumColorTextures() {
      return this.targets.length;
   }

   public InternalTextureFormat getColorTextureFormat(int index) {
      return this.formats[index];
   }

   public ImmutableSet<Integer> snapshot() {
      Builder<Integer> builder = ImmutableSet.builder();

      for (int i = 0; i < this.flipped.length; i++) {
         if (this.flipped[i]) {
            builder.add(i);
         }
      }

      return builder.build();
   }

   public IntList getBuffersToBeCleared() {
      return this.buffersToBeCleared;
   }

   public GlSampler getSamplerFor(int i) {
      if (this.hardwareFiltered[i]) {
         if (this.linearFiltered[i]) {
            return this.mipped[i] ? GlSampler.MIPPED_LINEAR_HW : GlSampler.LINEAR_HW;
         } else {
            return this.mipped[i] ? GlSampler.MIPPED_NEAREST_HW : GlSampler.NEAREST_HW;
         }
      } else if (this.linearFiltered[i]) {
         return this.mipped[i] ? GlSampler.MIPPED_LINEAR : GlSampler.LINEAR;
      } else {
         return this.mipped[i] ? GlSampler.MIPPED_NEAREST : GlSampler.NEAREST;
      }
   }
}
