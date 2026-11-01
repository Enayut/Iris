package net.irisshaders.iris.pipeline.programs;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.fallback.ShaderSynthesizer;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.ShaderPrinter;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.VanillaUniforms;
import net.irisshaders.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.IOUtils;

public class ShaderCreator {
   public static ShaderSupplier create(
      WorldRenderingPipeline pipeline,
      String name,
      ShaderKey shaderKey,
      ProgramSource source,
      ProgramId programId,
      GlFramebuffer writingToBeforeTranslucent,
      GlFramebuffer writingToAfterTranslucent,
      AlphaTest fallbackAlpha,
      VertexFormat vertexFormat,
      ShaderAttributeInputs inputs,
      FrameUpdateNotifier updateNotifier,
      IrisRenderingPipeline parent,
      Supplier<ImmutableSet<Integer>> flipped,
      FogMode fogMode,
      boolean isIntensity,
      boolean isFullbright,
      boolean isShadowPass,
      boolean isLines,
      CustomUniforms customUniforms,
      Patch patch
   ) throws IOException {
      AlphaTest alpha = source.getDirectives().getAlphaTestOverride().orElse(fallbackAlpha);
      BlendModeOverride blendModeOverride = source.getDirectives().getBlendModeOverride().orElse(programId.getBlendModeOverride());
      if (vertexFormat == null) {
         vertexFormat = WorldRenderingSettings.INSTANCE.getVertexFormat().getVertexFormat();
      }

      Map<PatchShaderType, String> transformed;
      if (patch == Patch.SODIUM) {
         transformed = TransformPatcher.patchSodium(
            name,
            source.getVertexSource().orElseThrow(RuntimeException::new),
            source.getGeometrySource().orElse(null),
            source.getTessControlSource().orElse(null),
            source.getTessEvalSource().orElse(null),
            source.getFragmentSource().orElseThrow(RuntimeException::new),
            alpha,
            pipeline.getTextureMap(),
            pipeline.getTextureOverrides(TextureStage.GBUFFERS_AND_SHADOW),
            false
         );
      } else {
         transformed = TransformPatcher.patchVanilla(
            name,
            source.getVertexSource().orElseThrow(RuntimeException::new),
            source.getGeometrySource().orElse(null),
            source.getTessControlSource().orElse(null),
            source.getTessEvalSource().orElse(null),
            source.getFragmentSource().orElseThrow(RuntimeException::new),
            alpha,
            isLines,
            shaderKey == ShaderKey.CLOUDS,
            true,
            inputs,
            pipeline.getTextureMap(),
            pipeline.getTextureOverrides(TextureStage.GBUFFERS_AND_SHADOW)
         );
      }

      String vertex = transformed.get(PatchShaderType.VERTEX);
      String geometry = transformed.get(PatchShaderType.GEOMETRY);
      String tessControl = transformed.get(PatchShaderType.TESS_CONTROL);
      String tessEval = transformed.get(PatchShaderType.TESS_EVAL);
      String fragment = transformed.get(PatchShaderType.FRAGMENT);
      String shaderJsonString = String.format(
         "    {\n    \"blend\": {\n        \"func\": \"add\",\n        \"srcrgb\": \"srcalpha\",\n        \"dstrgb\": \"1-srcalpha\"\n    },\n    \"vertex\": \"%s\",\n    \"fragment\": \"%s\",\n    \"attributes\": [\n        \"Position\",\n        \"Color\",\n        \"UV0\",\n        \"UV1\",\n        \"UV2\",\n        \"Normal\"\n    ],\n    \"uniforms\": [\n        { \"name\": \"iris_TextureMat\", \"type\": \"matrix4x4\", \"count\": 16, \"values\": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },\n        { \"name\": \"iris_ModelViewMat\", \"type\": \"matrix4x4\", \"count\": 16, \"values\": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },\n        { \"name\": \"iris_ModelViewMatInverse\", \"type\": \"matrix4x4\", \"count\": 16, \"values\": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },\n        { \"name\": \"iris_ProjMat\", \"type\": \"matrix4x4\", \"count\": 16, \"values\": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },\n        { \"name\": \"iris_ProjMatInverse\", \"type\": \"matrix4x4\", \"count\": 16, \"values\": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },\n        { \"name\": \"iris_NormalMat\", \"type\": \"matrix3x3\", \"count\": 9, \"values\": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0 ] },\n        { \"name\": \"iris_ModelOffset\", \"type\": \"float\", \"count\": 3, \"values\": [ 0.0, 0.0, 0.0 ] },\n        { \"name\": \"iris_ColorModulator\", \"type\": \"float\", \"count\": 4, \"values\": [ 1.0, 1.0, 1.0, 1.0 ] },\n        { \"name\": \"iris_GlintAlpha\", \"type\": \"float\", \"count\": 1, \"values\": [ 1.0 ] },\n        { \"name\": \"iris_FogStart\", \"type\": \"float\", \"count\": 1, \"values\": [ 0.0 ] },\n        { \"name\": \"iris_FogEnd\", \"type\": \"float\", \"count\": 1, \"values\": [ 1.0 ] },\n        { \"name\": \"iris_FogColor\", \"type\": \"float\", \"count\": 4, \"values\": [ 0.0, 0.0, 0.0, 0.0 ] },\n        {\n                    \"name\": \"iris_OverlayUV\",\n                    \"type\": \"int\",\n                    \"count\": 2,\n                    \"values\": [\n                        0,\n                        0\n                    ]\n                },\n                {\n                    \"name\": \"iris_LightUV\",\n                    \"type\": \"int\",\n                    \"count\": 2,\n                    \"values\": [\n                        0,\n                        0\n                    ]\n                }\n    ]\n}",
         name,
         name
      );
      ShaderPrinter.printProgram(name).addSources(transformed).addJson(shaderJsonString).print();
      new ShaderCreator.IrisProgramResourceFactory(shaderJsonString, vertex, geometry, tessControl, tessEval, fragment);
      List<BufferBlendOverride> overrides = new ArrayList<>();
      source.getDirectives().getBufferBlendOverrides().forEach(information -> {
         int index = Ints.indexOf(source.getDirectives().getDrawBuffers(), information.index());
         if (index > -1) {
            overrides.add(new BufferBlendOverride(index, information.blendMode()));
         }
      });
      PartialShader id = link(name, vertex, geometry, tessControl, tessEval, fragment, vertexFormat, false);
      VertexFormat finalVertexFormat = vertexFormat;
      return new ShaderSupplier(
         shaderKey,
         id,
         () -> {
            try {
               return new ExtendedShader(
                  id.getFinally(),
                  name,
                  finalVertexFormat,
                  tessControl != null || tessEval != null,
                  writingToBeforeTranslucent,
                  writingToAfterTranslucent,
                  blendModeOverride,
                  alpha,
                  uniforms -> {
                     CommonUniforms.addDynamicUniforms(uniforms, FogMode.PER_VERTEX);
                     customUniforms.assignTo(uniforms);
                     BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniforms);
                     VanillaUniforms.addVanillaUniforms(uniforms);
                  },
                  (samplerHolder, imageHolder) -> parent.addGbufferOrShadowSamplers(
                     samplerHolder, imageHolder, flipped, isShadowPass, inputs.hasTex(), inputs.hasLight(), inputs.hasOverlay()
                  ),
                  shaderKey,
                  parent,
                  overrides,
                  customUniforms,
                  patch
               );
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
      );
   }

   public static PartialShader link(
      String name, String vertex, String geometry, String tessControl, String tessEval, String fragment, VertexFormat vertexFormat, boolean isFallback
   ) throws ShaderCompileException {
      int i = GlStateManager.glCreateProgram();
      if (i <= 0) {
         throw new RuntimeException("Could not create shader program (returned program ID " + i + ")");
      }

      int vertexS = createShader(name, ShaderType.VERTEX, vertex);
      int geometryS = createShader(name, ShaderType.GEOMETRY, geometry);
      int tessContS = createShader(name, ShaderType.TESSELATION_CONTROL, tessControl);
      int tessEvalS = createShader(name, ShaderType.TESSELATION_EVAL, tessEval);
      int fragS = createShader(name, ShaderType.FRAGMENT, fragment);
      attachIfValid(i, vertexS);
      attachIfValid(i, geometryS);
      attachIfValid(i, tessContS);
      attachIfValid(i, tessEvalS);
      attachIfValid(i, fragS);
      ((VertexFormatExtension)vertexFormat).bindAttributesIris(isFallback, i);
      GlStateManager.glLinkProgram(i);
      return new PartialShader(i, vertexS, fragS, geometryS, tessContS, tessEvalS);
   }

   private static void attachIfValid(int i, int s) {
      if (s >= 0) {
         GlStateManager.glAttachShader(i, s);
      }
   }

   private static void detachIfValid(int i, int s) {
      if (s >= 0) {
         IrisRenderSystem.detachShader(i, s);
         GlStateManager.glDeleteShader(s);
      }
   }

   private static int createShader(String name, ShaderType shaderType, String source) {
      if (source == null) {
         return -1;
      }

      int shader = GlStateManager.glCreateShader(shaderType.id);
      GlStateManager.glShaderSource(shader, source);
      GlStateManager.glCompileShader(shader);
      String log = IrisRenderSystem.getShaderInfoLog(shader);
      if (!log.isEmpty()) {
         Iris.logger.warn("Shader compilation log for " + name + ": " + log);
      }

      int result = GlStateManager.glGetShaderi(shader, 35713);
      if (result != 1) {
         throw new ShaderCompileException(name, log);
      } else {
         return shader;
      }
   }

   public static ShaderSupplier createFallback(
      String name,
      ShaderKey shaderKey,
      GlFramebuffer writingToBeforeTranslucent,
      GlFramebuffer writingToAfterTranslucent,
      AlphaTest alpha,
      VertexFormat vertexFormat,
      BlendModeOverride blendModeOverride,
      IrisRenderingPipeline parent,
      FogMode fogMode,
      boolean entityLighting,
      boolean isGlint,
      boolean isText,
      boolean intensityTex,
      boolean isFullbright
   ) throws IOException {
      ShaderAttributeInputs inputs = new ShaderAttributeInputs(vertexFormat, isFullbright, false, isGlint, isText, false);
      if (vertexFormat == null) {
         vertexFormat = WorldRenderingSettings.INSTANCE.getVertexFormat().getVertexFormat();
      }

      boolean isLeash = vertexFormat == DefaultVertexFormat.POSITION_COLOR_LIGHTMAP;
      String vertex = ShaderSynthesizer.vsh(true, inputs, fogMode, entityLighting, isLeash);
      String fragment = ShaderSynthesizer.fsh(inputs, fogMode, alpha, intensityTex, isLeash);
      ShaderPrinter.printProgram(name).addSource(PatchShaderType.VERTEX, vertex).addSource(PatchShaderType.FRAGMENT, fragment).print();
      PartialShader id = link(name, vertex, null, null, null, fragment, vertexFormat, true);
      VertexFormat finalVertexFormat = vertexFormat;
      return new ShaderSupplier(
         shaderKey,
         id,
         () -> {
            try {
               GLDebug.nameObject(33506, id.program(), name + "_fallback");
               return new FallbackShader(
                  id.getFinally(),
                  RenderPipelines.ENTITY_CUTOUT,
                  name,
                  finalVertexFormat,
                  writingToBeforeTranslucent,
                  writingToAfterTranslucent,
                  blendModeOverride,
                  alpha.reference(),
                  parent
               );
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
      );
   }

   public static ShaderSupplier createFallbackShadow(
      String name,
      ShaderKey shaderKey,
      Supplier<ShadowRenderTargets> shadowSupplier,
      AlphaTest alpha,
      VertexFormat vertexFormat,
      BlendModeOverride blendModeOverride,
      IrisRenderingPipeline parent,
      FogMode fogMode,
      boolean entityLighting,
      boolean isGlint,
      boolean isText,
      boolean intensityTex,
      boolean isFullbright
   ) throws IOException {
      ShaderAttributeInputs inputs = new ShaderAttributeInputs(vertexFormat, isFullbright, false, isGlint, isText, false);
      if (vertexFormat == null) {
         vertexFormat = WorldRenderingSettings.INSTANCE.getVertexFormat().getVertexFormat();
      }

      boolean isLeash = vertexFormat == DefaultVertexFormat.POSITION_COLOR_LIGHTMAP;
      String vertex = ShaderSynthesizer.vsh(true, inputs, fogMode, entityLighting, isLeash);
      String fragment = ShaderSynthesizer.fsh(inputs, fogMode, alpha, intensityTex, isLeash);
      ShaderPrinter.printProgram(name).addSource(PatchShaderType.VERTEX, vertex).addSource(PatchShaderType.FRAGMENT, fragment).print();
      PartialShader id = link(name, vertex, null, null, null, fragment, vertexFormat, true);
      VertexFormat finalVertexFormat = vertexFormat;
      return new ShaderSupplier(
         shaderKey,
         id,
         () -> {
            try {
               GlFramebuffer framebuffer = shadowSupplier.get().createShadowFramebuffer(ImmutableSet.of(), new int[]{0});
               return new FallbackShader(
                  id.getFinally(),
                  RenderPipelines.ENTITY_CUTOUT,
                  name,
                  finalVertexFormat,
                  framebuffer,
                  framebuffer,
                  blendModeOverride,
                  alpha.reference(),
                  parent
               );
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
      );
   }

   public static ShaderSupplier createShadow(
      WorldRenderingPipeline pipeline,
      String name,
      ShaderKey shaderKey,
      ProgramSource source,
      ProgramId programId,
      Supplier<ShadowRenderTargets> shadowSupplier,
      AlphaTest fallbackAlpha,
      VertexFormat vertexFormat,
      ShaderAttributeInputs inputs,
      FrameUpdateNotifier updateNotifier,
      IrisRenderingPipeline parent,
      Supplier<ImmutableSet<Integer>> flipped,
      FogMode fogMode,
      boolean isIntensity,
      boolean isFullbright,
      boolean isShadowPass,
      boolean isLines,
      CustomUniforms customUniforms,
      Patch patchType
   ) throws IOException {
      AlphaTest alpha = source.getDirectives().getAlphaTestOverride().orElse(fallbackAlpha);
      BlendModeOverride blendModeOverride = source.getDirectives().getBlendModeOverride().orElse(programId.getBlendModeOverride());
      if (vertexFormat == null) {
         vertexFormat = WorldRenderingSettings.INSTANCE.getVertexFormat().getVertexFormat();
      }

      Map<PatchShaderType, String> transformed;
      if (patchType == Patch.SODIUM) {
         transformed = TransformPatcher.patchSodium(
            name,
            source.getVertexSource().orElseThrow(RuntimeException::new),
            source.getGeometrySource().orElse(null),
            source.getTessControlSource().orElse(null),
            source.getTessEvalSource().orElse(null),
            source.getFragmentSource().orElseThrow(RuntimeException::new),
            alpha,
            pipeline.getTextureMap(),
            pipeline.getTextureOverrides(TextureStage.GBUFFERS_AND_SHADOW),
            true
         );
      } else {
         transformed = TransformPatcher.patchVanilla(
            name,
            source.getVertexSource().orElseThrow(RuntimeException::new),
            source.getGeometrySource().orElse(null),
            source.getTessControlSource().orElse(null),
            source.getTessEvalSource().orElse(null),
            source.getFragmentSource().orElseThrow(RuntimeException::new),
            alpha,
            isLines,
            shaderKey == ShaderKey.CLOUDS,
            true,
            inputs,
            pipeline.getTextureMap(),
            pipeline.getTextureOverrides(TextureStage.GBUFFERS_AND_SHADOW)
         );
      }

      String vertex = transformed.get(PatchShaderType.VERTEX);
      String geometry = transformed.get(PatchShaderType.GEOMETRY);
      String tessControl = transformed.get(PatchShaderType.TESS_CONTROL);
      String tessEval = transformed.get(PatchShaderType.TESS_EVAL);
      String fragment = transformed.get(PatchShaderType.FRAGMENT);
      ShaderPrinter.printProgram(name).addSources(transformed).print();
      new ShaderCreator.IrisProgramResourceFactory("", vertex, geometry, tessControl, tessEval, fragment);
      List<BufferBlendOverride> overrides = new ArrayList<>();
      source.getDirectives().getBufferBlendOverrides().forEach(information -> {
         int index = Ints.indexOf(source.getDirectives().getDrawBuffers(), information.index());
         if (index > -1) {
            overrides.add(new BufferBlendOverride(index, information.blendMode()));
         }
      });
      PartialShader id = link(name, vertex, geometry, tessControl, tessEval, fragment, vertexFormat, false);
      VertexFormat finalVertexFormat = vertexFormat;
      return new ShaderSupplier(
         shaderKey,
         id,
         () -> {
            GlFramebuffer framebuffer = shadowSupplier.get()
               .createShadowFramebuffer(
                  ImmutableSet.of(), source.getDirectives().hasUnknownDrawBuffers() ? new int[]{0, 1} : source.getDirectives().getDrawBuffers()
               );

            try {
               return new ExtendedShader(
                  id.getFinally(),
                  name,
                  finalVertexFormat,
                  tessControl != null || tessEval != null,
                  framebuffer,
                  framebuffer,
                  blendModeOverride,
                  alpha,
                  uniforms -> {
                     CommonUniforms.addDynamicUniforms(uniforms, FogMode.PER_VERTEX);
                     customUniforms.assignTo(uniforms);
                     BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniforms);
                     VanillaUniforms.addVanillaUniforms(uniforms);
                  },
                  (samplerHolder, imageHolder) -> parent.addGbufferOrShadowSamplers(
                     samplerHolder, imageHolder, flipped, isShadowPass, inputs.hasTex(), inputs.hasLight(), inputs.hasOverlay()
                  ),
                  shaderKey,
                  parent,
                  overrides,
                  customUniforms,
                  patchType
               );
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
      );
   }

   private record IrisProgramResourceFactory(String json, String vertex, String geometry, String tessControl, String tessEval, String fragment)
      implements ResourceProvider {
      public Optional<Resource> getResource(Identifier id) {
         String path = id.getPath();
         if (path.endsWith("json")) {
            return Optional.of(new ShaderCreator.StringResource(id, this.json));
         } else if (path.endsWith("vsh")) {
            return Optional.of(new ShaderCreator.StringResource(id, this.vertex));
         } else if (path.endsWith("gsh")) {
            return this.geometry == null ? Optional.empty() : Optional.of(new ShaderCreator.StringResource(id, this.geometry));
         } else if (path.endsWith("tcs")) {
            return this.tessControl == null ? Optional.empty() : Optional.of(new ShaderCreator.StringResource(id, this.tessControl));
         } else if (path.endsWith("tes")) {
            return this.tessEval == null ? Optional.empty() : Optional.of(new ShaderCreator.StringResource(id, this.tessEval));
         } else {
            return path.endsWith("fsh") ? Optional.of(new ShaderCreator.StringResource(id, this.fragment)) : Optional.empty();
         }
      }
   }

   private static class StringResource extends Resource {
      private final String content;

      private StringResource(Identifier id, String content) {
         super(
            new PathPackResources(
               new PackLocationInfo(
                  "<iris shaderpack shaders>", Component.literal("iris"), PackSource.BUILT_IN, Optional.of(new KnownPack("iris", "shader", "1.0"))
               ),
               IrisPlatformHelpers.getInstance().getConfigDir()
            ),
            () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
         );
         this.content = content;
      }

      public InputStream open() {
         return IOUtils.toInputStream(this.content, StandardCharsets.UTF_8);
      }
   }
}
