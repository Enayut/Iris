package net.irisshaders.iris.pipeline.transform.transformer;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.abstract_node.ASTNode;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.Expression;
import io.github.douira.glsl_transformer.ast.node.expression.LiteralExpression;
import io.github.douira.glsl_transformer.ast.node.expression.ReferenceExpression;
import io.github.douira.glsl_transformer.ast.node.expression.binary.ArrayAccessExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.FunctionCallExpression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinFixedTypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinNumericTypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.TypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinFixedTypeSpecifier.BuiltinType.TypeKind;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.match.AutoHintedMatcher;
import io.github.douira.glsl_transformer.ast.query.match.Matcher;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.ast.transform.Template;
import io.github.douira.glsl_transformer.parser.ParseShape;
import io.github.douira.glsl_transformer.util.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import net.irisshaders.iris.pipeline.transform.parameter.SodiumParameters;
import net.irisshaders.iris.pipeline.transform.parameter.VanillaParameters;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

public class CommonTransformer {
   public static final AutoHintedMatcher<Expression> glTextureMatrix0 = new AutoHintedMatcher("gl_TextureMatrix[0]", ParseShape.EXPRESSION);
   public static final AutoHintedMatcher<Expression> glTextureMatrix1 = new AutoHintedMatcher("gl_TextureMatrix[1]", ParseShape.EXPRESSION);
   public static final AutoHintedMatcher<Expression> glTextureMatrix2 = new AutoHintedMatcher("gl_TextureMatrix[2]", ParseShape.EXPRESSION);
   public static final Matcher<ExternalDeclaration> sampler = new Matcher<ExternalDeclaration>("uniform Type name;", ParseShape.EXTERNAL_DECLARATION) {
      {
         this.markClassedPredicateWildcard(
            "type",
            ((Identifier)((ExternalDeclaration)this.pattern).getRoot().identifierIndex.getUnique("Type")).getAncestor(TypeSpecifier.class),
            BuiltinFixedTypeSpecifier.class,
            specifier -> specifier.type.kind == TypeKind.SAMPLER
         );
         this.markClassWildcard(
            "name*", ((Identifier)((ExternalDeclaration)this.pattern).getRoot().identifierIndex.getUnique("name")).getAncestor(DeclarationMember.class)
         );
      }
   };
   private static final AutoHintedMatcher<Expression> glFragDataI = new AutoHintedMatcher<Expression>("gl_FragData[index]", ParseShape.EXPRESSION) {
      {
         this.markClassedPredicateWildcard(
            "index",
            ((Identifier)((Expression)this.pattern).getRoot().identifierIndex.getUnique("index")).getAncestor(ReferenceExpression.class),
            LiteralExpression.class,
            literalExpression -> literalExpression.isInteger() && literalExpression.getInteger() >= 0L
         );
      }
   };
   private static final Template<ExternalDeclaration> fragDataDeclaration = Template.withExternalDeclaration("layout (location = __index) out vec4 __name;");
   private static final List<Expression> replaceExpressions = new ArrayList<>();
   private static final List<Long> replaceIndexes = new ArrayList<>();
   private static final Template<ExternalDeclaration> inputDeclarationTemplate = Template.withExternalDeclaration("uniform int __name;");
   private static final Template<ExternalDeclaration> inputDeclarationTemplateLayout = Template.withExternalDeclaration(
      "layout (location = __index) uniform int __name;"
   );

   static void injectForwardZProjection(ASTParser t, TranslationUnit tree) {
      DepthTransformer.injectForwardZProjection(t, tree, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
   }

   public static void transformDepth(ASTParser t, TranslationUnit tree, Root root, Parameters parameters) {
      Set<String> textureOverrides = new HashSet<>(parameters.getTextureOverrides());
      if (parameters.getTextureStage() != TextureStage.GBUFFERS_AND_SHADOW
         && (textureOverrides.contains("depthtex0") || textureOverrides.contains("gdepthtex"))) {
         textureOverrides.add("depthtex0");
         textureOverrides.add("gdepthtex");
      }

      DepthTransformer.transform(
         t, tree, root, parameters.type, isShadowPass(parameters, parameters.name), RenderSystem.getDevice().getDeviceInfo().isZZeroToOne(), textureOverrides
      );
   }

   public static void transformDepthPosition(ASTParser t, Map<PatchShaderType, TranslationUnit> trees, Parameters parameters) {
      DepthTransformer.transformPosition(t, trees, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne(), isShadowPass(parameters, parameters.name));
   }

   public static boolean isShadowPass(Parameters parameters, String name) {
      return parameters instanceof SodiumParameters sodium
         ? sodium.shadow
         : parameters.getTextureStage() == TextureStage.GBUFFERS_AND_SHADOW
            && name != null
            && (name.equals("shadow") || name.startsWith("shadow_") || name.endsWith("_shadow") || name.startsWith("dh_shadow_"));
   }

   static void renameFunctionCall(Root root, String oldName, String newName) {
      root.process(root.identifierIndex.getStream(oldName).filter(id -> id.getParent() instanceof FunctionCallExpression), id -> id.setName(newName));
   }

   static void renameAndWrapShadow(ASTParser t, Root root, String oldName, String innerName) {
      root.process(root.identifierIndex.getStream(oldName).filter(id -> id.getParent() instanceof FunctionCallExpression), id -> {
         FunctionCallExpression functionCall = (FunctionCallExpression)id.getParent();
         functionCall.getFunctionName().setName(innerName);
         FunctionCallExpression wrapper = (FunctionCallExpression)t.parseExpression(root, "vec4()");
         functionCall.replaceBy(wrapper);
         wrapper.getParameters().add(functionCall);
      });
   }

   public static void patchMultiTexCoord3(ASTParser t, TranslationUnit tree, Root root, Parameters parameters) {
      if (parameters.type.glShaderType == ShaderType.VERTEX && root.identifierIndex.has("gl_MultiTexCoord3") && !root.identifierIndex.has("mc_midTexCoord")) {
         root.rename("gl_MultiTexCoord3", "mc_midTexCoord");
         tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "attribute vec4 mc_midTexCoord;");
      }
   }

   public static void patchIntegerAttribute(ASTParser t, TranslationUnit tree, Root root, String name, String alias, int components) {
      if (components != 0) {
         Type dimension = Type.BOOL;

         for (Identifier id : root.identifierIndex.get(name)) {
            TypeAndInitDeclaration initDeclaration = (TypeAndInitDeclaration)id.getAncestor(2, 0, TypeAndInitDeclaration.class::isInstance);
            if (initDeclaration != null) {
               DeclarationExternalDeclaration declaration = (DeclarationExternalDeclaration)initDeclaration.getAncestor(
                  1, 0, DeclarationExternalDeclaration.class::isInstance
               );
               if (declaration != null && initDeclaration.getType().getTypeSpecifier() instanceof BuiltinNumericTypeSpecifier numeric) {
                  dimension = numeric.type;
                  declaration.detachAndDelete();
                  initDeclaration.detachAndDelete();
                  id.detachAndDelete();
                  break;
               }
            }
         }

         if (dimension != Type.BOOL) {
            root.replaceReferenceExpressions(t, name, alias);

            String declaration = switch (dimension) {
               case FLOAT32 -> "float " + alias + " = float(" + name + ".x);";
               case INT32 -> "int " + alias + " = " + name + ".x;";
               case F32VEC2 -> "vec2 " + alias + " = vec2(" + swizzle(name, components, 2, true) + ");";
               case F32VEC3 -> "vec3 " + alias + " = vec3(" + swizzle(name, components, 3, true) + ");";
               case F32VEC4 -> "vec4 " + alias + " = vec4(" + swizzle(name, components, 4, true) + ");";
               case I32VEC2 -> "ivec2 " + alias + " = ivec2(" + swizzle(name, components, 2, false) + ");";
               case I32VEC3 -> "ivec3 " + alias + " = ivec3(" + swizzle(name, components, 3, false) + ");";
               case I32VEC4 -> "ivec4 " + alias + " = ivec4(" + swizzle(name, components, 4, false) + ");";
               default -> throw new IllegalStateException("Got an invalid format for " + name + " (" + dimension.getCompactName() + ").");
            };
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, declaration);
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "in ivec" + components + " " + name + ";");
         }
      }
   }

   private static String swizzle(String name, int available, int declared, boolean floating) {
      StringBuilder builder = new StringBuilder();

      for (int i = 0; i < declared; i++) {
         if (i > 0) {
            builder.append(", ");
         }

         if (i < available) {
            builder.append(name).append('.').append("xyzw".charAt(i));
         } else if (i == 3) {
            builder.append(floating ? "1.0" : "1");
         } else {
            builder.append(floating ? "0.0" : "0");
         }
      }

      return builder.toString();
   }

   public static void replaceMidBlock(ASTParser t, TranslationUnit tree, Root root, Parameters parameters) {
      if (parameters.type.glShaderType == ShaderType.VERTEX && root.identifierIndex.has("at_midBlock")) {
         Type dimension = Type.F32VEC4;

         for (Identifier id : root.identifierIndex.get("at_midBlock")) {
            TypeAndInitDeclaration initDeclaration = (TypeAndInitDeclaration)id.getAncestor(2, 0, TypeAndInitDeclaration.class::isInstance);
            if (initDeclaration != null) {
               DeclarationExternalDeclaration declaration = (DeclarationExternalDeclaration)initDeclaration.getAncestor(
                  1, 0, DeclarationExternalDeclaration.class::isInstance
               );
               if (declaration != null && initDeclaration.getType().getTypeSpecifier() instanceof BuiltinNumericTypeSpecifier numeric) {
                  dimension = numeric.type;
                  declaration.detachAndDelete();
                  initDeclaration.detachAndDelete();
                  id.detachAndDelete();
                  break;
               }
            }
         }

         root.replaceReferenceExpressions(t, "at_midBlock", "iris_MidBlock");
         switch (dimension) {
            case FLOAT32:
               tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "float iris_MidBlock = at_midBlock.x * 127.0;");
               break;
            case INT32:
            default:
               throw new IllegalStateException("Got an invalid format at_midBlock (" + dimension.getCompactName() + ").");
            case F32VEC2:
               tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "vec2 iris_MidBlock = at_midBlock.xy * 127.0;");
               break;
            case F32VEC3:
               tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "vec3 iris_MidBlock = at_midBlock.xyz * 127.0;");
               break;
            case F32VEC4:
               tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "vec4 iris_MidBlock = at_midBlock * 127.0;");
         }

         tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "in vec4 at_midBlock;");
      }
   }

   public static void upgradeStorageQualifiers(ASTParser t, TranslationUnit tree, Root root, Parameters parameters) {
      for (StorageQualifier qualifier : root.nodeIndex.get(StorageQualifier.class)) {
         if (qualifier.storageType == StorageType.ATTRIBUTE) {
            qualifier.storageType = StorageType.IN;
         } else if (qualifier.storageType == StorageType.VARYING) {
            qualifier.storageType = parameters.type.glShaderType == ShaderType.VERTEX ? StorageType.OUT : StorageType.IN;
         }
      }
   }

   public static void transform(ASTParser t, TranslationUnit tree, Root root, Parameters parameters, boolean core) {
      root.rename("gl_FogFragCoord", "iris_FogFragCoord");
      if (parameters.type.glShaderType == ShaderType.VERTEX) {
         tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "out float iris_FogFragCoord;");
         tree.prependMainFunctionBody(t, "iris_FogFragCoord = 0.0f;");
      } else if (parameters.type.glShaderType == ShaderType.FRAGMENT) {
         tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "in float iris_FogFragCoord;");
      }

      if (parameters.type.glShaderType == ShaderType.VERTEX) {
         tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "vec4 iris_FrontColor;");
         root.rename("gl_FrontColor", "iris_FrontColor");
      }

      if (parameters.type.glShaderType == ShaderType.FRAGMENT) {
         if (root.identifierIndex.has("gl_FragColor")) {
            root.replaceReferenceExpressions(t, "gl_FragColor", "gl_FragData[0]");
         }

         if (root.identifierIndex.has("gl_TexCoord")) {
            root.rename("gl_TexCoord", "irs_texCoords");
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "in vec4 irs_texCoords[3];");
         }

         if (root.identifierIndex.has("gl_Color")) {
            root.rename("gl_Color", "irs_Color");
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "in vec4 irs_Color;");
         }

         replaceExpressions.clear();
         replaceIndexes.clear();
         Set<Long> replaceIndexesSet = new HashSet<>();

         for (Identifier id : root.identifierIndex.get("gl_FragData")) {
            ArrayAccessExpression accessExpression = (ArrayAccessExpression)id.getAncestor(ArrayAccessExpression.class);
            if (accessExpression != null && glFragDataI.matchesExtract(accessExpression)) {
               replaceExpressions.add(accessExpression);
               long index = ((LiteralExpression)glFragDataI.getNodeMatch("index", LiteralExpression.class)).getInteger();
               replaceIndexes.add(index);
               replaceIndexesSet.add(index);
            }
         }

         for (int i = 0; i < replaceExpressions.size(); i++) {
            replaceExpressions.get(i).replaceByAndDelete(new ReferenceExpression(new Identifier("iris_FragData" + replaceIndexes.get(i))));
         }

         for (long index : replaceIndexesSet) {
            tree.injectNode(
               ASTInjectionPoint.BEFORE_DECLARATIONS,
               (ExternalDeclaration)fragDataDeclaration.getInstanceFor(
                  root, new ASTNode[]{new LiteralExpression(Type.INT32, index), new Identifier("iris_FragData" + index)}
               )
            );
         }

         replaceExpressions.clear();
         replaceIndexes.clear();
         if (parameters.getAlphaTest() != AlphaTest.ALWAYS && !core && replaceIndexesSet.contains(0L)) {
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uniform float iris_currentAlphaTest;");
            tree.appendMainFunctionBody(t, parameters.getAlphaTest().toExpression("iris_FragData0.a", "iris_currentAlphaTest", "\t"));
         }
      }

      if (parameters.type.glShaderType == ShaderType.VERTEX || parameters.type.glShaderType == ShaderType.FRAGMENT) {
         upgradeStorageQualifiers(t, tree, root, parameters);
      }

      CommonTransformer.RenameTargetResult gcolorResult = getGtextureRenameTargets("gcolor", root);
      CommonTransformer.RenameTargetResult textureResult = getGtextureRenameTargets("texture", root);
      DeclarationMember samplerDeclarationMember = null;
      Stream<Identifier> targets = Stream.empty();
      if (gcolorResult != null) {
         samplerDeclarationMember = gcolorResult.samplerDeclarationMember;
         targets = Stream.concat(targets, gcolorResult.targets);
      }

      if (textureResult != null) {
         if (samplerDeclarationMember == null) {
            samplerDeclarationMember = textureResult.samplerDeclarationMember;
         } else {
            DeclarationMember secondDeclarationMember = textureResult.samplerDeclarationMember;
            if (((TypeAndInitDeclaration)secondDeclarationMember.getParent()).getMembers().size() == 1) {
               textureResult.samplerDeclaration.detachAndDelete();
            } else {
               secondDeclarationMember.detachAndDelete();
            }
         }

         targets = Stream.concat(targets, textureResult.targets);
      }

      if (samplerDeclarationMember != null) {
         samplerDeclarationMember.getName().setName("gtexture");
      }

      root.process(targets.filter(id -> !(id.getParent() instanceof FunctionCallExpression)), id -> id.setName("gtexture"));
      root.rename("gl_Fog", "irisInt_Fog");
      if (!(parameters instanceof VanillaParameters)) {
         tree.parseAndInjectNodes(
            t,
            ASTInjectionPoint.BEFORE_DECLARATIONS,
            new String[]{
               "uniform float iris_FogDensity;",
               "uniform float iris_FogStart;",
               "uniform float iris_FogEnd;",
               "uniform vec4 iris_FogColor;",
               "struct iris_FogParameters {vec4 color;float density;float start;float end;float scale;};",
               "iris_FogParameters irisInt_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0 / (iris_FogEnd - iris_FogStart));"
            }
         );
      }

      renameFunctionCall(root, "texture2D", "texture");
      renameFunctionCall(root, "texture3D", "texture");
      renameFunctionCall(root, "texture2DLod", "textureLod");
      renameFunctionCall(root, "texture3DLod", "textureLod");
      renameFunctionCall(root, "texture2DProj", "textureProj");
      renameFunctionCall(root, "texture3DProj", "textureProj");
      renameFunctionCall(root, "texture2DGrad", "textureGrad");
      renameFunctionCall(root, "texture2DGradARB", "textureGrad");
      renameFunctionCall(root, "texture3DGrad", "textureGrad");
      renameFunctionCall(root, "texelFetch2D", "texelFetch");
      renameFunctionCall(root, "texelFetch3D", "texelFetch");
      renameFunctionCall(root, "textureSize2D", "textureSize");
      renameAndWrapShadow(t, root, "shadow2D", "texture");
      renameAndWrapShadow(t, root, "shadow2DLod", "textureLod");
   }

   private static CommonTransformer.RenameTargetResult getGtextureRenameTargets(String name, Root root) {
      List<Identifier> gtextureTargets = new ArrayList<>();
      DeclarationExternalDeclaration samplerDeclaration = null;
      DeclarationMember samplerDeclarationMember = null;

      for (Identifier id : root.identifierIndex.get(name)) {
         gtextureTargets.add(id);
         if (samplerDeclaration == null) {
            DeclarationExternalDeclaration externalDeclaration = (DeclarationExternalDeclaration)id.getAncestor(
               3, 0, DeclarationExternalDeclaration.class::isInstance
            );
            if (externalDeclaration != null) {
               if (!sampler.matchesExtract(externalDeclaration)) {
                  return null;
               }

               boolean foundNameMatch = false;

               for (DeclarationMember member : ((TypeAndInitDeclaration)((DeclarationMember)sampler.getNodeMatch("name*", DeclarationMember.class))
                     .getAncestor(TypeAndInitDeclaration.class))
                  .getMembers()) {
                  if (member.getName().getName().equals(name)) {
                     foundNameMatch = true;
                  }
               }

               if (!foundNameMatch) {
                  return null;
               }

               samplerDeclaration = externalDeclaration;
               samplerDeclarationMember = (DeclarationMember)id.getAncestor(DeclarationMember.class);
               gtextureTargets.removeLast();
            }
         }
      }

      return samplerDeclaration == null
         ? null
         : new CommonTransformer.RenameTargetResult(samplerDeclaration, samplerDeclarationMember, gtextureTargets.stream());
   }

   public static void applyIntelHd4000Workaround(Root root) {
      root.rename("ftransform", "iris_ftransform");
   }

   public static void replaceGlMultiTexCoordBounded(ASTParser t, Root root, int minimum, int maximum) {
      root.replaceReferenceExpressions(t, root.getPrefixIdentifierIndex().prefixQueryFlat("gl_MultiTexCoord").filter(id -> {
         int index = Integer.parseInt(id.getName().substring("gl_MultiTexCoord".length()));
         return index >= minimum && index <= maximum;
      }), "vec4(0.0, 0.0, 0.0, 1.0)");
   }

   public static void addIfNotExists(Root root, ASTParser t, TranslationUnit tree, String name, Type type, StorageType storageType) {
      if (root.externalDeclarationIndex.getStream(name).noneMatch(entry -> entry.declaration() instanceof DeclarationExternalDeclaration)) {
         tree.injectNode(
            ASTInjectionPoint.BEFORE_DECLARATIONS,
            (ExternalDeclaration)inputDeclarationTemplate.getInstanceFor(
               root, new ASTNode[]{new StorageQualifier(storageType), new BuiltinNumericTypeSpecifier(type), new Identifier(name)}
            )
         );
      }
   }

   public static void addIfNotExists(Root root, ASTParser t, TranslationUnit tree, String name, Type type, StorageType storageType, int location) {
      if (root.externalDeclarationIndex.getStream(name).noneMatch(entry -> entry.declaration() instanceof DeclarationExternalDeclaration)) {
         tree.injectNode(
            ASTInjectionPoint.BEFORE_DECLARATIONS,
            (ExternalDeclaration)inputDeclarationTemplateLayout.getInstanceFor(
               root,
               new ASTNode[]{
                  new LiteralExpression(Type.INT32, location), new StorageQualifier(storageType), new BuiltinNumericTypeSpecifier(type), new Identifier(name)
               }
            )
         );
      }
   }

   static {
      fragDataDeclaration.markLocalReplacement("__index", ReferenceExpression.class);
      fragDataDeclaration.markIdentifierReplacement("__name");
      inputDeclarationTemplate.markLocalReplacement(inputDeclarationTemplate.getSourceRoot().nodeIndex.getOne(StorageQualifier.class));
      inputDeclarationTemplate.markLocalReplacement(inputDeclarationTemplate.getSourceRoot().nodeIndex.getOne(BuiltinNumericTypeSpecifier.class));
      inputDeclarationTemplate.markIdentifierReplacement("__name");
      inputDeclarationTemplateLayout.markLocalReplacement("__index", ReferenceExpression.class);
      inputDeclarationTemplateLayout.markLocalReplacement(inputDeclarationTemplateLayout.getSourceRoot().nodeIndex.getOne(StorageQualifier.class));
      inputDeclarationTemplateLayout.markLocalReplacement(inputDeclarationTemplateLayout.getSourceRoot().nodeIndex.getOne(BuiltinNumericTypeSpecifier.class));
      inputDeclarationTemplateLayout.markIdentifierReplacement("__name");
   }

   private record RenameTargetResult(DeclarationExternalDeclaration samplerDeclaration, DeclarationMember samplerDeclarationMember, Stream<Identifier> targets) {
   }
}
