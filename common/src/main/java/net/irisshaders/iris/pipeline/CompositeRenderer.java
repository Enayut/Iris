package net.irisshaders.iris.pipeline;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.backend.opengl.GlBuffer;
import com.mojang.renderpearl.backend.opengl.GlConst;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.GLDebug;
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
import net.irisshaders.iris.shaderpack.programs.ComputeSource;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.BufferFlipper;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

public class CompositeRenderer {
   public static final RenderPipeline COMPOSITE_PIPELINE = RenderPipeline.builder(new Snippet[0])
      .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
      .withColorTargetState(ColorTargetState.DEFAULT)
      .withLocation(Identifier.fromNamespaceAndPath("iris", "composite"))
      .withVertexShader(Identifier.fromNamespaceAndPath("iris", "core/screenquad"))
      .withFragmentShader("core/blit_screen")
      .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
      .withPrimitiveTopology(PrimitiveTopology.QUADS)
      .build();
   private final RenderTargets renderTargets;
   private final ImmutableList<CompositeRenderer.Pass> passes;
   private final TextureAccess noiseTexture;
   private final CenterDepthSampler centerDepthSampler;
   private final Object2ObjectMap<String, TextureAccess> customTextureIds;
   private final ImmutableSet<Integer> flippedAtLeastOnceFinal;
   private final CustomUniforms customUniforms;
   private final Object2ObjectMap<String, TextureAccess> irisCustomTextures;
   private final Set<GlImage> customImages;
   private final TextureStage textureStage;
   private final WorldRenderingPipeline pipeline;
   private final CompositePass compositePass;

   public CompositeRenderer(
      WorldRenderingPipeline pipeline,
      CompositePass compositePass,
      PackDirectives packDirectives,
      ProgramSource[] sources,
      ComputeSource[][] computes,
      RenderTargets renderTargets,
      ShaderStorageBufferHolder holder,
      TextureAccess noiseTexture,
      FrameUpdateNotifier updateNotifier,
      CenterDepthSampler centerDepthSampler,
      BufferFlipper bufferFlipper,
      Supplier<ShadowRenderTargets> shadowTargetsSupplier,
      TextureStage textureStage,
      Object2ObjectMap<String, TextureAccess> customTextureIds,
      Object2ObjectMap<String, TextureAccess> irisCustomTextures,
      Set<GlImage> customImages,
      ImmutableMap<Integer, Boolean> explicitPreFlips,
      CustomUniforms customUniforms
   ) {
      this.pipeline = pipeline;
      this.compositePass = compositePass;
      this.noiseTexture = noiseTexture;
      this.centerDepthSampler = centerDepthSampler;
      this.renderTargets = renderTargets;
      this.customTextureIds = customTextureIds;
      this.customUniforms = customUniforms;
      this.irisCustomTextures = irisCustomTextures;
      this.customImages = customImages;
      this.textureStage = textureStage;
      PackRenderTargetDirectives renderTargetDirectives = packDirectives.getRenderTargetDirectives();
      Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargetSettings = renderTargetDirectives.getRenderTargetSettings();
      Builder<CompositeRenderer.Pass> passes = ImmutableList.builder();
      com.google.common.collect.ImmutableSet.Builder<Integer> flippedAtLeastOnce = new com.google.common.collect.ImmutableSet.Builder();
      explicitPreFlips.forEach((bufferx, shouldFlip) -> {
         if (shouldFlip) {
            bufferFlipper.flip(bufferx);
         }
      });

      for (int i = 0; i < sources.length; i++) {
         ProgramSource source = sources[i];
         ImmutableSet<Integer> flipped = bufferFlipper.snapshot();
         ImmutableSet<Integer> flippedAtLeastOnceSnapshot = flippedAtLeastOnce.build();
         if (source != null && source.isValid()) {
            CompositeRenderer.Pass pass = new CompositeRenderer.Pass();
            ProgramDirectives directives = source.getDirectives();
            pass.name = source.getName();
            pass.program = this.createProgram(source, flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier);
            pass.blendModeOverride = source.getDirectives().getBlendModeOverride().orElse(null);
            if (computes.length != 0) {
               pass.computes = this.createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier, holder);
            } else {
               pass.computes = new ComputeProgram[0];
            }

            int[] drawBuffers = directives.getDrawBuffers();
            int passWidth = 0;
            int passHeight = 0;
            ImmutableMap<Integer, Boolean> explicitFlips = directives.getExplicitFlips();
            GlFramebuffer framebuffer = renderTargets.createColorFramebuffer(flipped, drawBuffers);

            for (int buffer : drawBuffers) {
               RenderTarget target = renderTargets.get(buffer);
               if (passWidth > 0 && passWidth != target.getWidth() || passHeight > 0 && passHeight != target.getHeight()) {
                  throw new IllegalStateException(
                     "Pass sizes must match for drawbuffers "
                        + Arrays.toString(drawBuffers)
                        + "\nOriginal width: "
                        + passWidth
                        + " New width: "
                        + target.getWidth()
                        + " Original height: "
                        + passHeight
                        + " New height: "
                        + target.getHeight()
                  );
               }

               passWidth = target.getWidth();
               passHeight = target.getHeight();
               if (explicitFlips.get(buffer) != Boolean.FALSE) {
                  bufferFlipper.flip(buffer);
                  flippedAtLeastOnce.add(buffer);
               }
            }

            explicitFlips.forEach((bufferx, shouldFlip) -> {
               if (shouldFlip) {
                  bufferFlipper.flip(bufferx);
                  flippedAtLeastOnce.add(bufferx);
               }
            });
            pass.drawBuffers = directives.getDrawBuffers();
            pass.viewWidth = passWidth;
            pass.viewHeight = passHeight;
            pass.stageReadsFromAlt = flipped;
            pass.framebuffer = framebuffer;
            pass.viewportScale = directives.getViewportScale();
            pass.mipmappedBuffers = directives.getMipmappedBuffers();
            pass.flippedAtLeastOnce = flippedAtLeastOnceSnapshot;
            passes.add(pass);
         } else if (computes.length != 0 && computes[i] != null && computes[i].length > 0) {
            CompositeRenderer.ComputeOnlyPass pass = new CompositeRenderer.ComputeOnlyPass();
            pass.name = computes[i].length > 0
               ? Arrays.stream(computes[i]).filter(Objects::nonNull).findFirst().map(ComputeSource::getName).orElse("unknown")
               : "unknown";
            pass.computes = this.createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier, holder);
            passes.add(pass);
         }
      }

      this.passes = passes.build();
      this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();
      GlStateManager._glBindFramebuffer(36008, 0);
   }

   private static void setupMipmapping(RenderTarget target, boolean readFromAlt) {
      if (target != null) {
         int texture = readFromAlt ? target.getAltTexture() : target.getMainTexture();
         IrisRenderSystem.generateMipmaps(texture, 3553);
         target.turnOnMips(readFromAlt);
      }
   }

   private boolean hasComputes(ComputeSource[][] computes) {
      boolean hasCompute = false;

      for (int i = 0; i < computes.length; i++) {
         if (computes[i].length > 0) {
            for (int j = 0; j < computes[i].length; j++) {
               if (computes[i][j] != null) {
                  hasCompute = true;
                  break;
               }
            }
         }
      }

      return hasCompute;
   }

   public ImmutableSet<Integer> getFlippedAtLeastOnceFinal() {
      return this.flippedAtLeastOnceFinal;
   }

   public void recalculateSizes() {
      UnmodifiableIterator var1 = this.passes.iterator();

      while (var1.hasNext()) {
         CompositeRenderer.Pass pass = (CompositeRenderer.Pass)var1.next();
         if (!(pass instanceof CompositeRenderer.ComputeOnlyPass)) {
            int passWidth = 0;
            int passHeight = 0;

            for (int buffer : pass.drawBuffers) {
               RenderTarget target = this.renderTargets.get(buffer);
               if (passWidth > 0 && passWidth != target.getWidth() || passHeight > 0 && passHeight != target.getHeight()) {
                  throw new IllegalStateException("Pass widths must match");
               }

               passWidth = target.getWidth();
               passHeight = target.getHeight();
            }

            this.renderTargets.destroyFramebuffer(pass.framebuffer);
            pass.framebuffer = this.renderTargets.createColorFramebuffer(pass.stageReadsFromAlt, pass.drawBuffers);
            pass.viewWidth = passWidth;
            pass.viewHeight = passHeight;
         }
      }
   }

   public void renderAll() {
      ImmediateState.temporarilyIgnorePass = true;
      GLDebug.pushGroup(20 + this.compositePass.ordinal(), this.compositePass.name().toLowerCase(Locale.ROOT));
      com.mojang.blaze3d.pipeline.RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      GpuBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(6);
      IndexType type = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).type();
      FullScreenQuadRenderer.INSTANCE.bind();
      GlStateManager._colorMask(15);
      int i = 0;

      for (int passesSize = this.passes.size(); i < passesSize; i++) {
         CompositeRenderer.Pass compositePass = (CompositeRenderer.Pass)this.passes.get(i);
         GLDebug.pushGroup(20 * this.compositePass.ordinal() + i, compositePass.name);
         boolean ranCompute = false;

         for (ComputeProgram computeProgram : compositePass.computes) {
            if (computeProgram != null) {
               ranCompute = true;
               computeProgram.use();
               this.customUniforms.push(computeProgram);
               computeProgram.dispatch(main.width, main.height);
            }
         }

         if (ranCompute) {
            IrisRenderSystem.memoryBarrier(8232);
         }

         Program.unbind();
         if (compositePass instanceof CompositeRenderer.ComputeOnlyPass) {
            GLDebug.popGroup();
         } else {
            if (!compositePass.mipmappedBuffers.isEmpty()) {
               GlStateManager._activeTexture(33984);
               UnmodifiableIterator var13 = compositePass.mipmappedBuffers.iterator();

               while (var13.hasNext()) {
                  int index = (Integer)var13.next();
                  setupMipmapping(this.renderTargets.get(index), compositePass.stageReadsFromAlt.contains(index));
               }
            }

            compositePass.setupState();
            float scaledWidth = compositePass.viewWidth * compositePass.viewportScale.scale();
            float scaledHeight = compositePass.viewHeight * compositePass.viewportScale.scale();
            int beginWidth = (int)(compositePass.viewWidth * compositePass.viewportScale.viewportX());
            int beginHeight = (int)(compositePass.viewHeight * compositePass.viewportScale.viewportY());
            GlStateManager._viewport(beginWidth, beginHeight, (int)scaledWidth, (int)scaledHeight);
            GlStateManager._scissorBox(beginWidth, beginHeight, (int)scaledWidth, (int)scaledHeight);
            GlStateManager._disableScissorTest();
            compositePass.program.use();
            this.customUniforms.push(compositePass.program);
            GlStateManager._glBindBuffer(34963, ((GlBuffer)indices).handle);
            GlStateManager._drawElements(4, 6, GlConst.toGl(type), 0L);
            BlendModeOverride.restore();
            GLDebug.popGroup();
         }
      }

      ProgramUniforms.clearActiveUniforms();
      ProgramSamplers.clearActiveSamplers();
      GlStateManager._glUseProgram(0);

      for (int ix = 0; ix < SamplerLimits.get().getMaxTextureUnits(); ix++) {
         if (GlStateManagerAccessor.getTEXTURES()[ix].binding != 0) {
            GlStateManager._activeTexture(33984 + ix);
            GlStateManager._bindTexture(0);
         }
      }

      GlStateManager._activeTexture(33984);
      GLDebug.popGroup();
      ImmediateState.temporarilyIgnorePass = false;
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
         this.textureStage,
         this.pipeline.getTextureMap(),
         this.pipeline.getTextureOverrides(this.textureStage)
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
         throw new RuntimeException("Shader compilation failed for " + source.getName() + "!", e);
      }

      CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
      this.customUniforms.assignTo(builder);
      ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(
         builder, this.customTextureIds, flippedAtLeastOnceSnapshot
      );
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
                  this.textureStage,
                  this.pipeline.getTextureMap(),
                  this.pipeline.getTextureOverrides(this.textureStage)
               );
               ShaderPrinter.printProgram(source.getName()).addSource(PatchShaderType.COMPUTE, transformed).print();
               builder = ProgramBuilder.beginCompute(source.getName(), transformed, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
            } catch (ShaderCompileException e) {
               throw e;
            } catch (RuntimeException e) {
               throw new RuntimeException("Shader compilation failed for compute " + source.getName() + "!", e);
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
      UnmodifiableIterator var1 = this.passes.iterator();

      while (var1.hasNext()) {
         CompositeRenderer.Pass renderPass = (CompositeRenderer.Pass)var1.next();
         renderPass.destroy();
      }
   }

   private static class ComputeOnlyPass extends CompositeRenderer.Pass {
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
      int[] drawBuffers;
      int viewWidth;
      int viewHeight;
      String name;
      Program program;
      BlendModeOverride blendModeOverride;
      ComputeProgram[] computes;
      GlFramebuffer framebuffer;
      ImmutableSet<Integer> flippedAtLeastOnce;
      ImmutableSet<Integer> stageReadsFromAlt;
      ImmutableSet<Integer> mipmappedBuffers;
      ViewportData viewportScale;

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
         GlStateManager._colorMask(-1);
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
