package net.irisshaders.iris.shadows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.framebuffer.ViewportData;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.mixinterface.CustomPass;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.ShaderPrinter;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.samplers.IrisImages;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.FilledIndirectPointer;
import net.irisshaders.iris.shaderpack.programs.ComputeSource;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;

public class ShadowCompositeRenderer {
   private final ShadowRenderTargets renderTargets;
   private final ImmutableList<ShadowCompositeRenderer.Pass> passes;
   private final TextureAccess noiseTexture;
   private final Object2ObjectMap<String, TextureAccess> customTextureIds;
   private final ImmutableSet<Integer> flippedAtLeastOnceFinal;
   private final CustomUniforms customUniforms;
   private final Object2ObjectMap<String, TextureAccess> irisCustomTextures;
   private final WorldRenderingPipeline pipeline;
   private final Set<GlImage> irisCustomImages;

   public ShadowCompositeRenderer(
      WorldRenderingPipeline pipeline,
      PackDirectives packDirectives,
      ProgramSource[] sources,
      ComputeSource[][] computes,
      ShadowRenderTargets renderTargets,
      ShaderStorageBufferHolder holder,
      TextureAccess noiseTexture,
      FrameUpdateNotifier updateNotifier,
      Object2ObjectMap<String, TextureAccess> customTextureIds,
      Set<GlImage> customImages,
      ImmutableMap<Integer, Boolean> explicitPreFlips,
      Object2ObjectMap<String, TextureAccess> irisCustomTextures,
      CustomUniforms customUniforms
   ) {
      this.pipeline = pipeline;
      this.noiseTexture = noiseTexture;
      this.renderTargets = renderTargets;
      this.customTextureIds = customTextureIds;
      this.irisCustomTextures = irisCustomTextures;
      this.irisCustomImages = customImages;
      this.customUniforms = customUniforms;
      PackRenderTargetDirectives renderTargetDirectives = packDirectives.getRenderTargetDirectives();
      Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargetSettings = renderTargetDirectives.getRenderTargetSettings();
      Builder<ShadowCompositeRenderer.Pass> passes = ImmutableList.builder();
      com.google.common.collect.ImmutableSet.Builder<Integer> flippedAtLeastOnce = new com.google.common.collect.ImmutableSet.Builder();
      explicitPreFlips.forEach((bufferx, shouldFlip) -> {
         if (shouldFlip) {
            renderTargets.flip(bufferx);
         }
      });
      int i = 0;

      for (int sourcesLength = sources.length; i < sourcesLength; i++) {
         ProgramSource source = sources[i];
         ImmutableSet<Integer> flipped = renderTargets.snapshot();
         ImmutableSet<Integer> flippedAtLeastOnceSnapshot = flippedAtLeastOnce.build();
         if (source != null && source.isValid()) {
            ShadowCompositeRenderer.Pass pass = new ShadowCompositeRenderer.Pass();
            ProgramDirectives directives = source.getDirectives();
            pass.name = source.getName();
            pass.program = this.createProgram(source, flipped, flippedAtLeastOnceSnapshot, renderTargets);
            pass.blendModeOverride = source.getDirectives().getBlendModeOverride().orElse(null);
            if (computes.length > 0) {
               pass.computes = this.createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, renderTargets, holder);
            } else {
               pass.computes = new ComputeProgram[0];
            }

            int[] drawBuffers = source.getDirectives().hasUnknownDrawBuffers() ? new int[]{0, 1} : source.getDirectives().getDrawBuffers();
            GlFramebuffer framebuffer = renderTargets.createColorFramebuffer(flipped, drawBuffers);
            pass.stageReadsFromAlt = flipped;
            pass.framebuffer = framebuffer;
            pass.viewportScale = directives.getViewportScale();
            pass.mipmappedBuffers = directives.getMipmappedBuffers();
            pass.flippedAtLeastOnce = flippedAtLeastOnceSnapshot;
            passes.add(pass);
            ImmutableMap<Integer, Boolean> explicitFlips = directives.getExplicitFlips();

            for (int buffer : drawBuffers) {
               if (explicitFlips.get(buffer) != Boolean.FALSE) {
                  renderTargets.flip(buffer);
                  flippedAtLeastOnce.add(buffer);
               }
            }

            explicitFlips.forEach((bufferx, shouldFlip) -> {
               if (shouldFlip) {
                  renderTargets.flip(bufferx);
                  flippedAtLeastOnce.add(bufferx);
               }
            });
         } else if (computes.length > 0 && computes[i] != null) {
            ShadowCompositeRenderer.ComputeOnlyPass pass = new ShadowCompositeRenderer.ComputeOnlyPass();
            pass.computes = this.createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, renderTargets, holder);
            passes.add(pass);
         }
      }

      this.passes = passes.build();
      this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();
      GlStateManager._glBindFramebuffer(36008, 0);
   }

   private static void setupMipmapping(RenderTarget target, boolean readFromAlt) {
      int texture = readFromAlt ? target.getAltTexture() : target.getMainTexture();
      IrisRenderSystem.generateMipmaps(texture, 3553);
      IrisRenderSystem.texParameteri(texture, 3553, 10241, target.getInternalFormat().getPixelFormat().isInteger() ? 9984 : 9987);
   }

   private static void resetRenderTarget(RenderTarget target) {
      int filter = 9729;
      if (target.getInternalFormat().getPixelFormat().isInteger()) {
         filter = 9728;
      }

      IrisRenderSystem.texParameteri(target.getMainTexture(), 3553, 10241, filter);
      IrisRenderSystem.texParameteri(target.getAltTexture(), 3553, 10241, filter);
   }

   public ImmutableSet<Integer> getFlippedAtLeastOnceFinal() {
      return this.flippedAtLeastOnceFinal;
   }

   public void renderAll() {
      GpuBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(6);
      IndexType type = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).type();
      RenderPass pass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(() -> "Shadow composites", Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(), Optional.empty());

      try {
         pass.setPipeline(RenderSystem.getCompiledPipeline(CompositeRenderer.COMPOSITE_PIPELINE));
         pass.setVertexBuffer(0, FullScreenQuadRenderer.INSTANCE.getQuad().slice());
         pass.setIndexBuffer(indices, type);
         UnmodifiableIterator var4 = this.passes.iterator();

         while (var4.hasNext()) {
            ShadowCompositeRenderer.Pass renderPass = (ShadowCompositeRenderer.Pass)var4.next();
            boolean ranCompute = false;

            for (ComputeProgram computeProgram : renderPass.computes) {
               if (computeProgram != null) {
                  ranCompute = true;
                  computeProgram.use();
                  this.customUniforms.push(computeProgram);
                  com.mojang.blaze3d.pipeline.RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                  computeProgram.dispatch(main.width, main.height);
               }
            }

            if (ranCompute) {
               IrisRenderSystem.memoryBarrier(8232);
            }

            Program.unbind();
            if (!(renderPass instanceof ShadowCompositeRenderer.ComputeOnlyPass)) {
               if (!renderPass.mipmappedBuffers.isEmpty()) {
                  GlStateManager._activeTexture(33984);
                  UnmodifiableIterator var14 = renderPass.mipmappedBuffers.iterator();

                  while (var14.hasNext()) {
                     int index = (Integer)var14.next();
                     setupMipmapping(this.renderTargets.get(index), renderPass.stageReadsFromAlt.contains(index));
                  }
               }

               pass.iris$setCustomPass(renderPass);
               float scaledWidth = this.renderTargets.getResolution() * renderPass.viewportScale.scale();
               float scaledHeight = this.renderTargets.getResolution() * renderPass.viewportScale.scale();
               int beginWidth = (int)(this.renderTargets.getResolution() * renderPass.viewportScale.viewportX());
               int beginHeight = (int)(this.renderTargets.getResolution() * renderPass.viewportScale.viewportY());
               GlStateManager._viewport(beginWidth, beginHeight, (int)scaledWidth, (int)scaledHeight);
               GlStateManager._scissorBox(beginWidth, beginHeight, (int)scaledWidth, (int)scaledHeight);
               GlStateManager._disableScissorTest();
               renderPass.framebuffer.bind();
               renderPass.program.use();
               this.customUniforms.push(renderPass.program);
               pass.drawIndexed(6, 1, 0, 0, 0);
               BlendModeOverride.restore();
            }
         }
      } catch (Throwable var13) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var12) {
               var13.addSuppressed(var12);
            }
         }

         throw var13;
      }

      if (pass != null) {
         pass.close();
      }

      ProgramUniforms.clearActiveUniforms();
      GlStateManager._glUseProgram(0);
      GlStateManager._activeTexture(33984);
   }

   private Program createProgram(
      ProgramSource source, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot, ShadowRenderTargets targets
   ) {
      Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(
         source.getName(),
         source.getVertexSource().orElseThrow(NullPointerException::new),
         source.getGeometrySource().orElse(null),
         source.getFragmentSource().orElseThrow(NullPointerException::new),
         TextureStage.SHADOWCOMP,
         this.pipeline.getTextureMap(),
         this.pipeline.getTextureOverrides(TextureStage.SHADOWCOMP)
      );
      String vertex = transformed.get(PatchShaderType.VERTEX);
      String geometry = transformed.get(PatchShaderType.GEOMETRY);
      String fragment = transformed.get(PatchShaderType.FRAGMENT);
      ShaderPrinter.printProgram(source.getName()).addSources(transformed).print();
      Objects.requireNonNull(flipped);

      ProgramBuilder builder;
      try {
         builder = ProgramBuilder.begin(source.getName(), vertex, geometry, fragment, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
      } catch (RuntimeException e) {
         throw new RuntimeException("Shader compilation failed for shadow composite " + source.getName() + "!", e);
      }

      ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(
         builder, this.customTextureIds, flippedAtLeastOnceSnapshot
      );
      CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
      this.customUniforms.assignTo(builder);
      IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, this.noiseTexture);
      IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, this.irisCustomTextures);
      IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, targets, flipped, this.pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
      IrisImages.addShadowColorImages(builder, targets, flipped);
      IrisImages.addCustomImages(builder, this.irisCustomImages);
      IrisSamplers.addCustomImages(builder, this.irisCustomImages);
      Program build = builder.build();
      this.customUniforms.mapholderToPass(builder, build);
      return build;
   }

   private ComputeProgram[] createComputes(
      ComputeSource[] sources,
      ImmutableSet<Integer> flipped,
      ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
      ShadowRenderTargets targets,
      ShaderStorageBufferHolder holder
   ) {
      ComputeProgram[] programs = new ComputeProgram[sources.length];

      for (int i = 0; i < programs.length; i++) {
         ComputeSource source = sources[i];
         if (source != null && !source.getSource().isEmpty()) {
            Objects.requireNonNull(flipped);

            ProgramBuilder builder;
            try {
               String transformed = TransformPatcher.patchCompute(
                  source.getName(),
                  source.getSource().orElse(null),
                  TextureStage.SHADOWCOMP,
                  this.pipeline.getTextureMap(),
                  this.pipeline.getTextureOverrides(TextureStage.SHADOWCOMP)
               );
               ShaderPrinter.printProgram(source.getName()).addSource(PatchShaderType.COMPUTE, transformed).print();
               builder = ProgramBuilder.beginCompute(source.getName(), transformed, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
            } catch (RuntimeException e) {
               throw new RuntimeException("Shader compilation failed for shadowcomp compute " + source.getName() + "!", e);
            }

            ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(
               builder, this.customTextureIds, flippedAtLeastOnceSnapshot
            );
            CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
            this.customUniforms.assignTo(builder);
            IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, this.noiseTexture);
            IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, this.irisCustomTextures);
            IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, targets, flipped, this.pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
            IrisImages.addShadowColorImages(builder, targets, flipped);
            IrisImages.addCustomImages(builder, this.irisCustomImages);
            IrisSamplers.addCustomImages(builder, this.irisCustomImages);
            programs[i] = builder.buildCompute();
            this.customUniforms.mapholderToPass(builder, programs[i]);
            programs[i]
               .setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups(), FilledIndirectPointer.basedOff(holder, source.getIndirectPointer()));
         }
      }

      return programs;
   }

   public void destroy() {
      UnmodifiableIterator var1 = this.passes.iterator();

      while (var1.hasNext()) {
         ShadowCompositeRenderer.Pass renderPass = (ShadowCompositeRenderer.Pass)var1.next();
         renderPass.destroy();
      }
   }

   private static class ComputeOnlyPass extends ShadowCompositeRenderer.Pass {
      @Override
      protected void destroy() {
         for (ComputeProgram compute : this.computes) {
            if (compute != null) {
               compute.destroy();
            }
         }
      }
   }

   private static class Pass implements CustomPass {
      String name;
      Program program;
      BlendModeOverride blendModeOverride;
      GlFramebuffer framebuffer;
      ImmutableSet<Integer> flippedAtLeastOnce;
      ImmutableSet<Integer> stageReadsFromAlt;
      ImmutableSet<Integer> mipmappedBuffers;
      ViewportData viewportScale;
      ComputeProgram[] computes;

      protected void destroy() {
         this.program.destroy();

         for (ComputeProgram compute : this.computes) {
            if (compute != null) {
               compute.destroy();
            }
         }
      }

      @Override
      public void setupState() {
         this.framebuffer.bind();
         if (this.blendModeOverride != null) {
            this.blendModeOverride.apply();
         } else {
            BlendModeOverride.restore();
            IrisRenderSystem.disableBlend();
         }
      }
   }
}
