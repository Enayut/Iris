package net.irisshaders.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.abstract_node.ASTNode;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.match.AutoHintedMatcher;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.parser.ParseShape;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.parameter.SodiumParameters;

public class SodiumCoreTransformer {
   public static final AutoHintedMatcher<ExternalDeclaration> modelViewMatrix = new AutoHintedMatcher(
      "uniform mat4 modelViewMatrix;", ParseShape.EXTERNAL_DECLARATION
   );
   public static final AutoHintedMatcher<ExternalDeclaration> projectionMatrix = new AutoHintedMatcher(
      "uniform mat4 projectionMatrix;", ParseShape.EXTERNAL_DECLARATION
   );

   public static void transform(ASTParser t, TranslationUnit tree, Root root, SodiumParameters parameters) {
      root.rename("alphaTestRef", "iris_currentAlphaTest");
      root.processMatches(t, modelViewMatrix, ASTNode::detachAndDelete);
      root.rename("modelViewMatrix", "u_ModelViewMatrix");
      root.rename("modelViewMatrixInverse", "iris_ModelViewMatInverse");
      root.processMatches(t, projectionMatrix, ASTNode::detachAndDelete);
      CommonTransformer.injectForwardZProjection(t, tree);
      root.replaceReferenceExpressions(t, "projectionMatrix", "iris_undoRevZ(u_ProjectionMatrix)");
      root.rename("projectionMatrixInverse", "iris_ProjMatInverse");
      root.rename("normalMatrix", "iris_NormalMat");
      root.replaceReferenceExpressions(t, "chunkOffset", "iris_RegionOffset");
      root.replaceReferenceExpressions(t, "u_RegionOffset", "iris_RegionOffset");
      if (parameters.type == PatchShaderType.VERTEX) {
         boolean needsNormal = root.identifierIndex.has("vaNormal") || root.identifierIndex.has("at_tangent");
         root.replaceReferenceExpressions(t, "vaPosition", "_vert_position + _get_draw_translation(_draw_id)");
         root.replaceReferenceExpressions(t, "vaColor", "_vert_color");
         root.replaceReferenceExpressions(t, "vaNormal", "irs_Normal");
         root.replaceReferenceExpressions(t, "at_tangent", "irs_Tangent");
         root.replaceReferenceExpressions(t, "vaUV0", "_vert_tex_diffuse_coord");
         root.replaceReferenceExpressions(t, "vaUV1", "ivec2(0, 10)");
         root.replaceReferenceExpressions(t, "vaUV2", "a_LightAndData.xy");
         root.replaceReferenceExpressions(t, "textureMatrix", "mat4(1.0)");
         SodiumTransformer.replaceMidTexCoord(t, tree, root, 3.0517578E-5F);
         SodiumTransformer.replaceMCEntity(t, tree, root);
         CommonTransformer.replaceMidBlock(t, tree, root, parameters);
         SodiumTransformer.injectVertInit(t, tree, root, parameters, needsNormal);
      }

      tree.parseAndInjectNode(
         t,
         ASTInjectionPoint.BEFORE_DECLARATIONS,
         "layout(std140) uniform u_Globals {\n    mat4 u_ProjectionMatrix;\n    mat4 u_ModelViewMatrix;\n\n    vec4 u_FogColor;\n    vec2 u_EnvironmentFog;\n    vec2 u_RenderFog;\n\n    vec2 u_TexelSize;\n    vec2 u_TexCoordShrink;\n\n    float u_FadePeriodInv;\n    bool u_UseRGSS;\n};"
      );
   }
}
