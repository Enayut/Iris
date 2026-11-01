package net.irisshaders.iris.pipeline;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.mixin.GlStateManagerAccessor;
import net.irisshaders.iris.mixinterface.CustomPass;
import net.irisshaders.iris.pathways.CenterDepthSampler;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.ShaderPrinter;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.samplers.IrisImages;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.FilledIndirectPointer;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ComputeSource;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.Blaze3dRenderTargetExt;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL46C;

public class FinalPassRenderer {
   private static final CustomPass STATE = new CustomPass() {
      @Override
      public void setupState() {
      }
   };
   private final RenderTargets renderTargets;
   @Nullable
   private final FinalPassRenderer.Pass finalPass;
   private final ImmutableList<FinalPassRenderer.SwapPass> swapPasses;
   private final GlFramebuffer baseline;
   private final GlFramebuffer colorHolder;
   private final Object2ObjectMap<String, TextureAccess> irisCustomTextures;
   private final Set<GlImage> customImages;
   private final TextureAccess noiseTexture;
   private final CenterDepthSampler centerDepthSampler;
   private final Object2ObjectMap<String, TextureAccess> customTextureIds;
   private final CustomUniforms customUniforms;
   private final WorldRenderingPipeline pipeline;
   private int lastColorTextureId;
   private int lastColorTextureVersion;

   public FinalPassRenderer(
      WorldRenderingPipeline pipeline,
      ProgramSet pack,
      RenderTargets renderTargets,
      TextureAccess noiseTexture,
      ShaderStorageBufferHolder holder,
      FrameUpdateNotifier updateNotifier,
      ImmutableSet<Integer> flippedBuffers,
      CenterDepthSampler centerDepthSampler,
      Supplier<ShadowRenderTargets> shadowTargetsSupplier,
      Object2ObjectMap<String, TextureAccess> customTextureIds,
      Object2ObjectMap<String, TextureAccess> irisCustomTextures,
      Set<GlImage> customImages,
      ImmutableSet<Integer> flippedAtLeastOnce,
      CustomUniforms customUniforms
   ) {
      this.pipeline = pipeline;
      this.centerDepthSampler = centerDepthSampler;
      this.customTextureIds = customTextureIds;
      this.irisCustomTextures = irisCustomTextures;
      this.customImages = customImages;
      PackRenderTargetDirectives renderTargetDirectives = pack.getPackDirectives().getRenderTargetDirectives();
      Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargetSettings = renderTargetDirectives.getRenderTargetSettings();
      this.noiseTexture = noiseTexture;
      this.renderTargets = renderTargets;
      this.customUniforms = customUniforms;
      this.finalPass = pack.get(ProgramId.Final).map(source -> {
         FinalPassRenderer.Pass pass = new FinalPassRenderer.Pass();
         ProgramDirectives directives = source.getDirectives();
         pass.program = this.createProgram(source, flippedBuffers, flippedAtLeastOnce, shadowTargetsSupplier);
         pass.computes = this.createComputes(pack.getFinalCompute(), flippedBuffers, flippedAtLeastOnce, shadowTargetsSupplier, holder);
         pass.stageReadsFromAlt = flippedBuffers;
         pass.mipmappedBuffers = directives.getMipmappedBuffers();
         return pass;
      }).orElse(null);
      IntList buffersToBeCleared = pack.getPackDirectives().getRenderTargetDirectives().getBuffersToBeCleared();
      this.baseline = renderTargets.createGbufferFramebuffer(flippedBuffers, new int[]{0});
      this.colorHolder = new GlFramebuffer();
      this.lastColorTextureId = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture().iris$getGlId();
      this.lastColorTextureVersion = ((Blaze3dRenderTargetExt)Minecraft.getInstance().gameRenderer.mainRenderTarget()).iris$getColorBufferVersion();
      this.colorHolder.addColorAttachment(0, this.lastColorTextureId);
      Builder<FinalPassRenderer.SwapPass> swapPasses = ImmutableList.builder();
      flippedBuffers.forEach(i -> {
         int target = i;
         if (!buffersToBeCleared.contains(target)) {
            FinalPassRenderer.SwapPass swap = new FinalPassRenderer.SwapPass();
            RenderTarget target1 = renderTargets.getOrCreate(target);
            swap.target = target;
            swap.width = target1.getWidth();
            swap.height = target1.getHeight();
            swap.from = renderTargets.createColorFramebuffer(ImmutableSet.of(), new int[]{target});
            swap.targetTexture = renderTargets.get(target).getMainTexture();
            swapPasses.add(swap);
         }
      });
      this.swapPasses = swapPasses.build();
      GlStateManager._glBindFramebuffer(36008, 0);
   }

   private static void setupMipmapping(RenderTarget target, boolean readFromAlt) {
      if (target != null) {
         int texture = readFromAlt ? target.getAltTexture() : target.getMainTexture();
         IrisRenderSystem.generateMipmaps(texture, 3553);
         target.turnOnMips(readFromAlt);
      }
   }

   private static void resetRenderTarget(RenderTarget target) {
      if (target != null) {
         target.turnOffMips(true);
         target.turnOffMips(false);
      }
   }

   public void renderFinalPass() {
      com.mojang.blaze3d.pipeline.RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      int baseWidth = main.width;
      int baseHeight = main.height;
      if (((Blaze3dRenderTargetExt)main).iris$getColorBufferVersion() != this.lastColorTextureVersion
         || main.getColorTexture().iris$getGlId() != this.lastColorTextureId) {
         this.lastColorTextureVersion = ((Blaze3dRenderTargetExt)main).iris$getColorBufferVersion();
         this.lastColorTextureId = main.getColorTexture().iris$getGlId();
         this.colorHolder.addColorAttachment(0, this.lastColorTextureId);
      }

      if (this.finalPass != null) {
         GLDebug.pushGroup(990, "final");

         for (ComputeProgram computeProgram : this.finalPass.computes) {
            if (computeProgram != null) {
               computeProgram.use();
               this.customUniforms.push(computeProgram);
               computeProgram.dispatch(baseWidth, baseHeight);
            }
         }

         IrisRenderSystem.memoryBarrier(8232);
         if (!this.finalPass.mipmappedBuffers.isEmpty()) {
            GlStateManager._activeTexture(33984);
            UnmodifiableIterator var11 = this.finalPass.mipmappedBuffers.iterator();

            while (var11.hasNext()) {
               int index = (Integer)var11.next();
               setupMipmapping(this.renderTargets.get(index), this.finalPass.stageReadsFromAlt.contains(index));
            }
         }

         GpuBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(6);
         IndexType type = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).type();
         RenderPass renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(() -> "Final pass", Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(), Optional.empty());

         try {
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(CompositeRenderer.COMPOSITE_PIPELINE));
            renderPass.setIndexBuffer(indices, type);
            renderPass.setVertexBuffer(0, FullScreenQuadRenderer.INSTANCE.getQuad().slice());
            renderPass.iris$setCustomPass(STATE);
            this.finalPass.program.use();
            BlendModeOverride.restore();
            IrisRenderSystem.disableBlend();
            this.customUniforms.push(this.finalPass.program);
            renderPass.drawIndexed(6, 1, 0, 0, 0);
         } catch (Throwable var10) {
            if (renderPass != null) {
               try {
                  renderPass.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (renderPass != null) {
            renderPass.close();
         }

         GLDebug.popGroup();
      } else {
         this.baseline.bindAsReadBuffer();
         IrisRenderSystem.copyTexSubImage2D(main.getColorTexture().iris$getGlId(), 3553, 0, 0, 0, 0, 0, baseWidth, baseHeight);
      }

      GlStateManager._activeTexture(33984);

      for (int i = 0; i < this.renderTargets.getRenderTargetCount(); i++) {
         resetRenderTarget(this.renderTargets.get(i));
      }

      UnmodifiableIterator var14 = this.swapPasses.iterator();

      while (var14.hasNext()) {
         FinalPassRenderer.SwapPass swapPass = (FinalPassRenderer.SwapPass)var14.next();
         swapPass.from.bind();
         GlStateManager._bindTexture(swapPass.targetTexture);
         GL46C.glCopyTexSubImage2D(3553, 0, 0, 0, 0, 0, swapPass.width, swapPass.height);
      }

      ProgramUniforms.clearActiveUniforms();
      ProgramSamplers.clearActiveSamplers();
      GlStateManager._glUseProgram(0);

      for (int i = 0; i < SamplerLimits.get().getMaxTextureUnits(); i++) {
         if (GlStateManagerAccessor.getTEXTURES()[i].binding != 0) {
            GlStateManager._activeTexture(33984 + i);
            GlStateManager._bindTexture(0);
         }
      }

      GlStateManager._activeTexture(33984);
   }

   public void recalculateSwapPassSize() {
      UnmodifiableIterator var1 = this.swapPasses.iterator();

      while (var1.hasNext()) {
         FinalPassRenderer.SwapPass swapPass = (FinalPassRenderer.SwapPass)var1.next();
         RenderTarget target = this.renderTargets.get(swapPass.target);
         this.renderTargets.destroyFramebuffer(swapPass.from);
         swapPass.from = this.renderTargets.createColorFramebuffer(ImmutableSet.of(), new int[]{swapPass.target});
         swapPass.width = target.getWidth();
         swapPass.height = target.getHeight();
         swapPass.targetTexture = target.getMainTexture();
      }
   }

   private Program createProgram(
      ProgramSource source,
      ImmutableSet<Integer> flipped,
      ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
      Supplier<ShadowRenderTargets> shadowTargetsSupplier
   ) {
      Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(
         source.getName(),
         source.getVertexSource().orElseThrow(NullPointerException::new),
         source.getGeometrySource().orElse(null),
         source.getFragmentSource().orElseThrow(NullPointerException::new),
         TextureStage.COMPOSITE_AND_FINAL,
         this.pipeline.getTextureMap(),
         this.pipeline.getTextureOverrides(TextureStage.COMPOSITE_AND_FINAL)
      );
      String vertex = transformed.get(PatchShaderType.VERTEX);
      String geometry = transformed.get(PatchShaderType.GEOMETRY);
      String fragment = transformed.get(PatchShaderType.FRAGMENT);
      ShaderPrinter.printProgram(source.getName()).addSources(transformed).print();
      Objects.requireNonNull(flipped);

      ProgramBuilder builder;
      try {
         builder = ProgramBuilder.begin(source.getName(), vertex, geometry, fragment, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
      } catch (ShaderCompileException e) {
         throw e;
      } catch (RuntimeException e) {
         throw new RuntimeException("Shader compilation failed for final!", e);
      }

      CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
      this.customUniforms.assignTo(builder);
      ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(
         builder, this.customTextureIds, flippedAtLeastOnceSnapshot
      );
      IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flipped, this.renderTargets, true, this.pipeline);
      IrisSamplers.addCustomImages(customTextureSamplerInterceptor, this.customImages);
      IrisImages.addRenderTargetImages(builder, () -> flipped, this.renderTargets);
      IrisImages.addCustomImages(builder, this.customImages);
      IrisSamplers.addCustomTextures(builder, this.irisCustomTextures);
      IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, this.noiseTexture);
      IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, this.renderTargets);
      if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
         IrisSamplers.addShadowSamplers(
            customTextureSamplerInterceptor, shadowTargetsSupplier.get(), null, this.pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS)
         );
         IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get(), null);
      }

      this.centerDepthSampler.setUsage(builder.addDynamicSampler(this.centerDepthSampler::getCenterDepthTexture, GlSampler.NEAREST, "iris_centerDepthSmooth"));
      Program build = builder.build();
      this.customUniforms.mapholderToPass(builder, build);
      return build;
   }

   private ComputeProgram[] createComputes(
      ComputeSource[] compute,
      ImmutableSet<Integer> flipped,
      ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
      Supplier<ShadowRenderTargets> shadowTargetsSupplier,
      ShaderStorageBufferHolder holder
   ) {
      ComputeProgram[] programs = new ComputeProgram[compute.length];

      for (int i = 0; i < programs.length; i++) {
         ComputeSource source = compute[i];
         if (source != null && !source.getSource().isEmpty()) {
            Objects.requireNonNull(flipped);

            ProgramBuilder builder;
            try {
               String transformed = TransformPatcher.patchCompute(
                  source.getName(),
                  source.getSource().orElse(null),
                  TextureStage.COMPOSITE_AND_FINAL,
                  this.pipeline.getTextureMap(),
                  this.pipeline.getTextureOverrides(TextureStage.COMPOSITE_AND_FINAL)
               );
               ShaderPrinter.printProgram(source.getName()).addSource(PatchShaderType.COMPUTE, transformed).print();
               builder = ProgramBuilder.beginCompute(source.getName(), transformed, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
            } catch (ShaderCompileException e) {
               throw e;
            } catch (RuntimeException e) {
               throw new RuntimeException("Shader compilation failed for final compute " + source.getName() + "!", e);
            }

            ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(
               builder, this.customTextureIds, flippedAtLeastOnceSnapshot
            );
            CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
            this.customUniforms.assignTo(builder);
            IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flipped, this.renderTargets, true, this.pipeline);
            IrisSamplers.addCustomTextures(builder, this.irisCustomTextures);
            IrisSamplers.addCustomImages(customTextureSamplerInterceptor, this.customImages);
            IrisImages.addRenderTargetImages(builder, () -> flipped, this.renderTargets);
            IrisImages.addCustomImages(builder, this.customImages);
            IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, this.noiseTexture);
            IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, this.renderTargets);
            if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
               IrisSamplers.addShadowSamplers(
                  customTextureSamplerInterceptor, shadowTargetsSupplier.get(), null, this.pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS)
               );
               IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get(), null);
            }

            this.centerDepthSampler
               .setUsage(builder.addDynamicSampler(this.centerDepthSampler::getCenterDepthTexture, GlSampler.NEAREST, "iris_centerDepthSmooth"));
            programs[i] = builder.buildCompute();
            this.customUniforms.mapholderToPass(builder, programs[i]);
            programs[i]
               .setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups(), FilledIndirectPointer.basedOff(holder, source.getIndirectPointer()));
         }
      }

      return programs;
   }

   public void destroy() {
      if (this.finalPass != null) {
         this.finalPass.destroy();
      }

      this.colorHolder.destroy();
   }

   private static final class Pass {
      Program program;
      ComputeProgram[] computes;
      ImmutableSet<Integer> stageReadsFromAlt;
      ImmutableSet<Integer> mipmappedBuffers;

      private void destroy() {
         this.program.destroy();
      }
   }

   private static final class SwapPass {
      public int target;
      public int width;
      public int height;
      GlFramebuffer from;
      int targetTexture;
   }
}
