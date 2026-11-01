package net.irisshaders.iris.pipeline.transform;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.Profile;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.VersionStatement;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.transform.EnumASTTransformer;
import io.github.douira.glsl_transformer.ast.transform.SingleASTTransformer;
import io.github.douira.glsl_transformer.ast.transform.TransformationException;
import io.github.douira.glsl_transformer.ast.transform.ASTParser.ParsingCacheStrategy;
import io.github.douira.glsl_transformer.parser.ParsingException;
import io.github.douira.glsl_transformer.token_filter.ChannelFilter;
import io.github.douira.glsl_transformer.token_filter.TokenChannel;
import io.github.douira.glsl_transformer.token_filter.TokenFilter;
import io.github.douira.glsl_transformer.util.LRUCache;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.parameter.ComputeParameters;
import net.irisshaders.iris.pipeline.transform.parameter.DHParameters;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import net.irisshaders.iris.pipeline.transform.parameter.SodiumParameters;
import net.irisshaders.iris.pipeline.transform.parameter.TextureStageParameters;
import net.irisshaders.iris.pipeline.transform.parameter.VanillaParameters;
import net.irisshaders.iris.pipeline.transform.transformer.CommonTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.CompatibilityTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.CompositeCoreTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.CompositeTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.DHGenericTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.DHTerrainTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.DepthTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.GlintTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.SodiumCoreTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.SodiumTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.TextureTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.VanillaCoreTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.VanillaTransformer;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import org.antlr.v4.runtime.Token;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TransformPatcher {
   static final TokenFilter<Parameters> parseTokenFilter = new ChannelFilter<Parameters>(TokenChannel.PREPROCESSOR) {
      public boolean isTokenAllowed(Token token) {
         if (!super.isTokenAllowed(token)) {
            throw new IllegalArgumentException(
               "Unparsed preprocessor directives such as '" + token.getText() + "' may not be present at this stage of shader processing!"
            );
         } else {
            return true;
         }
      }
   };
   private static final boolean useCache = true;
   private static final Map<TransformPatcher.CacheKey, Map<PatchShaderType, String>> cache = new LRUCache(400);
   private static final List<String> internalPrefixes = List.of("iris_", "irisMain", "moj_import");
   private static final Pattern versionPattern = Pattern.compile("#version\\s+(\\d+)", 32);
   private static final EnumASTTransformer<Parameters, PatchShaderType> transformer = new EnumASTTransformer<Parameters, PatchShaderType>(PatchShaderType.class) {
      {
         this.setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT);
         this.setParsingCacheStrategy(ParsingCacheStrategy.TWO_TIER);
      }

      public TranslationUnit parseTranslationUnit(Root rootInstance, String input) {
         Matcher matcher = TransformPatcher.versionPattern.matcher(input);
         if (!matcher.find()) {
            throw new IllegalArgumentException("No #version directive found in source code! See debugging.md for more information.");
         }

         TransformPatcher.transformer.getLexer().version = Version.fromNumber(Integer.parseInt(matcher.group(1)));
         return super.parseTranslationUnit(rootInstance, input);
      }
   };
   static Logger LOGGER = LogManager.getLogger(TransformPatcher.class);

   private static Map<PatchShaderType, String> transformInternal(String name, Map<PatchShaderType, String> inputs, Parameters parameters) {
      try {
         parameters.name = name;
         return (Map<PatchShaderType, String>)transformer.transform(inputs, parameters);
      } catch (TransformationException | ParsingException | IllegalStateException | IllegalArgumentException e) {
         ShaderPrinter.printProgram("errored_" + name).addSources(inputs).print();
         throw new ShaderCompileException(name, e);
      }
   }

   private static Map<PatchShaderType, String> transform(
      String name, String vertex, String geometry, String tessControl, String tessEval, String fragment, Parameters parameters
   ) {
      if (vertex == null && geometry == null && tessControl == null && tessEval == null && fragment == null) {
         return null;
      }

      Map<PatchShaderType, String> result = null;
      TransformPatcher.CacheKey key = new TransformPatcher.CacheKey(parameters, name, vertex, geometry, tessControl, tessEval, fragment);
      if (cache.containsKey(key)) {
         result = cache.get(key);
      }

      if (result == null) {
         transformer.setPrintType(Iris.getIrisConfig().areDebugOptionsEnabled() ? PrintType.INDENTED : PrintType.SIMPLE);
         EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
         inputs.put(PatchShaderType.VERTEX, vertex);
         inputs.put(PatchShaderType.GEOMETRY, geometry);
         inputs.put(PatchShaderType.TESS_CONTROL, tessControl);
         inputs.put(PatchShaderType.TESS_EVAL, tessEval);
         inputs.put(PatchShaderType.FRAGMENT, fragment);
         result = transformInternal(name, inputs, parameters);
         cache.put(key, result);
      }

      return result;
   }

   private static Map<PatchShaderType, String> transformCompute(String name, String compute, Parameters parameters) {
      if (compute == null) {
         return null;
      }

      Map<PatchShaderType, String> result = null;
      TransformPatcher.CacheKey key = new TransformPatcher.CacheKey(parameters, name, compute);
      if (cache.containsKey(key)) {
         result = cache.get(key);
      }

      if (result == null) {
         transformer.setPrintType(Iris.getIrisConfig().areDebugOptionsEnabled() ? PrintType.INDENTED : PrintType.SIMPLE);
         EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
         inputs.put(PatchShaderType.COMPUTE, compute);
         result = transformInternal(name, inputs, parameters);
         cache.put(key, result);
      }

      return result;
   }

   public static String patchDepth(String input, ShaderType type) {
      if (input == null) {
         return null;
      }

      Matcher matcher = versionPattern.matcher(input);
      if (!matcher.find()) {
         throw new IllegalArgumentException("No #version directive found in source code!");
      }

      PatchShaderType patchType = PatchShaderType.fromGlShaderType(type)[0];
      boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
      SingleASTTransformer<Parameters> depthTransformer = new SingleASTTransformer();
      depthTransformer.setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT);
      depthTransformer.setTokenFilter(parseTokenFilter);
      depthTransformer.setPrintType(PrintType.SIMPLE);
      depthTransformer.getLexer().version = Version.fromNumber(Integer.parseInt(matcher.group(1)));
      depthTransformer.setTransformation((tree, root) -> root.indexBuildSession(() -> {
         DepthTransformer.transform(depthTransformer, tree, root, patchType, false, zZeroToOne);
         DepthTransformer.transformPosition(depthTransformer, Map.of(patchType, tree), zZeroToOne, false);
      }));
      return (String)depthTransformer.transform(input);
   }

   public static Map<PatchShaderType, String> patchVanilla(
      String name,
      String vertex,
      String geometry,
      String tessControl,
      String tessEval,
      String fragment,
      AlphaTest alpha,
      boolean isLines,
      boolean isClouds,
      boolean hasChunkOffset,
      ShaderAttributeInputs inputs,
      Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
      Set<String> textureOverrides
   ) {
      return transform(
         name,
         vertex,
         geometry,
         tessControl,
         tessEval,
         fragment,
         new VanillaParameters(
            Patch.VANILLA,
            textureMap,
            textureOverrides,
            alpha,
            isLines,
            isClouds,
            hasChunkOffset,
            inputs,
            geometry != null,
            tessControl != null || tessEval != null
         )
      );
   }

   public static Map<PatchShaderType, String> patchDHTerrain(
      String name,
      String vertex,
      String tessControl,
      String tessEval,
      String geometry,
      String fragment,
      Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
      Set<String> textureOverrides
   ) {
      return transform(name, vertex, geometry, tessControl, tessEval, fragment, new DHParameters(Patch.DH_TERRAIN, textureMap, textureOverrides));
   }

   public static Map<PatchShaderType, String> patchDHGeneric(
      String name,
      String vertex,
      String tessControl,
      String tessEval,
      String geometry,
      String fragment,
      Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
      Set<String> textureOverrides
   ) {
      return transform(name, vertex, geometry, tessControl, tessEval, fragment, new DHParameters(Patch.DH_GENERIC, textureMap, textureOverrides));
   }

   public static Map<PatchShaderType, String> patchSodium(
      String name,
      String vertex,
      String geometry,
      String tessControl,
      String tessEval,
      String fragment,
      AlphaTest alpha,
      Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
      Set<String> textureOverrides,
      boolean shadow
   ) {
      return transform(name, vertex, geometry, tessControl, tessEval, fragment, new SodiumParameters(Patch.SODIUM, textureMap, textureOverrides, alpha, shadow));
   }

   public static Map<PatchShaderType, String> patchComposite(
      String name,
      String vertex,
      String geometry,
      String fragment,
      TextureStage stage,
      Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
      Set<String> textureOverrides
   ) {
      return transform(name, vertex, geometry, null, null, fragment, new TextureStageParameters(Patch.COMPOSITE, stage, textureMap, textureOverrides));
   }

   public static String patchCompute(
      String name,
      String compute,
      TextureStage stage,
      Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
      Set<String> textureOverrides
   ) {
      return transformCompute(name, compute, new ComputeParameters(Patch.COMPUTE, stage, textureMap, textureOverrides))
         .getOrDefault(PatchShaderType.COMPUTE, null);
   }

   static {
      transformer.setTransformation(
         (trees, parameters) -> {
            for (PatchShaderType type : PatchShaderType.values()) {
               TranslationUnit tree = (TranslationUnit)trees.get(type);
               if (tree != null) {
                  tree.outputOptions.enablePrintInfo();
                  parameters.type = type;
                  Root root = tree.getRoot();
                  internalPrefixes.stream()
                     .<Identifier>flatMap(root.getPrefixIdentifierIndex()::prefixQueryFlat)
                     .findAny()
                     .ifPresent(
                        id -> {
                           throw new IllegalArgumentException(
                              "Detected a potential reference to unstable and internal Iris shader interfaces (iris_, irisMain and moj_import). This isn't currently supported. Violation: "
                                 + id.getName()
                                 + ". See debugging.md for more information."
                           );
                        }
                     );
                  root.indexBuildSession(() -> {
                     VersionStatement versionStatement = tree.getVersionStatement();
                     if (versionStatement == null) {
                        throw new IllegalStateException("Missing the version statement!");
                     }

                     Profile profile = versionStatement.profile;
                     Version version = versionStatement.version;
                     if (Objects.requireNonNull(parameters.patch) == Patch.COMPUTE) {
                        versionStatement.profile = Profile.CORE;
                        CommonTransformer.transform(transformer, tree, root, parameters, true);
                     } else {
                        boolean isLine = parameters.patch == Patch.VANILLA && ((VanillaParameters)parameters).isLines();
                        if (profile != Profile.CORE && (version.number < 150 || profile != null) && !isLine) {
                           if (version.number < 330) {
                              versionStatement.version = Version.GLSL33;
                           }

                           versionStatement.profile = Profile.CORE;
                           switch (parameters.patch) {
                              case COMPOSITE:
                                 CompositeTransformer.transform(transformer, tree, root, parameters);
                                 break;
                              case SODIUM:
                                 SodiumParameters sodiumParameters = (SodiumParameters)parameters;
                                 SodiumTransformer.transform(transformer, tree, root, sodiumParameters);
                                 break;
                              case VANILLA:
                                 VanillaTransformer.transform(transformer, tree, root, (VanillaParameters)parameters);
                                 break;
                              case DH_TERRAIN:
                                 DHTerrainTransformer.transform(transformer, tree, root, parameters);
                                 break;
                              case DH_GENERIC:
                                 DHGenericTransformer.transform(transformer, tree, root, parameters);
                                 break;
                              default:
                                 throw new UnsupportedOperationException("Unknown patch type: " + parameters.patch);
                           }
                        } else {
                           if (version.number < 330) {
                              versionStatement.version = Version.GLSL33;
                           }

                           switch (parameters.patch) {
                              case COMPOSITE:
                                 CompositeCoreTransformer.transform(transformer, tree, root, parameters);
                                 break;
                              case SODIUM:
                                 SodiumParameters sodiumParameters = (SodiumParameters)parameters;
                                 SodiumCoreTransformer.transform(transformer, tree, root, sodiumParameters);
                                 break;
                              case VANILLA:
                                 VanillaCoreTransformer.transform(transformer, tree, root, (VanillaParameters)parameters);
                                 break;
                              default:
                                 throw new UnsupportedOperationException("Unknown patch type: " + parameters.patch);
                           }

                           if (parameters.type == PatchShaderType.FRAGMENT) {
                              CompatibilityTransformer.transformFragmentCore(transformer, tree, root, parameters);
                           }
                        }
                     }

                     GlintTransformer.transform(transformer, tree, root, parameters);
                     TextureTransformer.transform(transformer, tree, root, parameters.getTextureStage(), parameters.getTextureMap());
                     CommonTransformer.transformDepth(transformer, tree, root, parameters);
                     CompatibilityTransformer.transformEach(transformer, tree, root, parameters);
                  });
               }
            }

            GlintTransformer.transformGrouped(transformer, trees, parameters);
            CompatibilityTransformer.transformGrouped(transformer, trees, parameters);
            CommonTransformer.transformDepthPosition(transformer, trees, parameters);
         }
      );
      transformer.setTokenFilter(parseTokenFilter);
   }

   private static class CacheKey {
      final boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
      final boolean shadow;
      final Parameters parameters;
      final String vertex;
      final String geometry;
      final String tessControl;
      final String tessEval;
      final String fragment;
      final String compute;

      public CacheKey(Parameters parameters, String name, String vertex, String geometry, String tessControl, String tessEval, String fragment) {
         this.parameters = parameters;
         this.shadow = CommonTransformer.isShadowPass(parameters, name);
         this.vertex = vertex;
         this.geometry = geometry;
         this.tessControl = tessControl;
         this.tessEval = tessEval;
         this.fragment = fragment;
         this.compute = null;
      }

      public CacheKey(Parameters parameters, String name, String compute) {
         this.parameters = parameters;
         this.shadow = CommonTransformer.isShadowPass(parameters, name);
         this.vertex = null;
         this.geometry = null;
         this.tessControl = null;
         this.tessEval = null;
         this.fragment = null;
         this.compute = compute;
      }

      @Override
      public int hashCode() {
         int prime = 31;
         int result = 1;
         result = 31 * result + Boolean.hashCode(this.zZeroToOne);
         result = 31 * result + Boolean.hashCode(this.shadow);
         result = 31 * result + (this.parameters == null ? 0 : this.parameters.hashCode());
         result = 31 * result + (this.vertex == null ? 0 : this.vertex.hashCode());
         result = 31 * result + (this.geometry == null ? 0 : this.geometry.hashCode());
         result = 31 * result + (this.tessControl == null ? 0 : this.tessControl.hashCode());
         result = 31 * result + (this.tessEval == null ? 0 : this.tessEval.hashCode());
         result = 31 * result + (this.fragment == null ? 0 : this.fragment.hashCode());
         return 31 * result + (this.compute == null ? 0 : this.compute.hashCode());
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }

         if (obj == null) {
            return false;
         }

         if (this.getClass() != obj.getClass()) {
            return false;
         }

         TransformPatcher.CacheKey other = (TransformPatcher.CacheKey)obj;
         if (this.zZeroToOne == other.zZeroToOne && this.shadow == other.shadow) {
            if (this.parameters == null) {
               if (other.parameters != null) {
                  return false;
               }
            } else if (!this.parameters.equals(other.parameters)) {
               return false;
            }

            if (this.vertex == null) {
               if (other.vertex != null) {
                  return false;
               }
            } else if (!this.vertex.equals(other.vertex)) {
               return false;
            }

            if (this.geometry == null) {
               if (other.geometry != null) {
                  return false;
               }
            } else if (!this.geometry.equals(other.geometry)) {
               return false;
            }

            if (this.tessControl == null) {
               if (other.tessControl != null) {
                  return false;
               }
            } else if (!this.tessControl.equals(other.tessControl)) {
               return false;
            }

            if (this.tessEval == null) {
               if (other.tessEval != null) {
                  return false;
               }
            } else if (!this.tessEval.equals(other.tessEval)) {
               return false;
            }

            if (this.fragment == null) {
               if (other.fragment != null) {
                  return false;
               }
            } else if (!this.fragment.equals(other.fragment)) {
               return false;
            }

            return this.compute == null ? other.compute == null : this.compute.equals(other.compute);
         } else {
            return false;
         }
      }
   }
}
