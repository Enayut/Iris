package net.irisshaders.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.parameter.VanillaParameters;

public class VanillaTransformer {
   public static void transform(ASTParser t, TranslationUnit tree, Root root, VanillaParameters parameters) {
      if (parameters.inputs.hasOverlay()) {
         EntityPatcher.patchOverlayColor(t, tree, root, parameters);
         EntityPatcher.patchEntityId(t, tree, root, parameters);
      } else if (parameters.inputs.isText()) {
         EntityPatcher.patchEntityId(t, tree, root, parameters);
      }

      CommonTransformer.transform(t, tree, root, parameters, false);
      CommonTransformer.replaceMidBlock(t, tree, root, parameters);
      tree.parseAndInjectNodes(
         t,
         ASTInjectionPoint.BEFORE_DECLARATIONS,
         new String[]{
            "const float mc_chunkFade = -1.0;",
            "layout(std140) uniform iris_Fog {\n    vec4 FogColor;\n    float FogEnvironmentalStart;\n    float FogEnvironmentalEnd;\n    float FogRenderDistanceStart;\n    float FogRenderDistanceEnd;\n    float FogSkyEnd;\n    float FogCloudsEnd;\n} iris_fogP;\n",
            "struct iris_FogParameters {vec4 color;float density;float start;float end;float scale;};",
            "iris_FogParameters irisInt_Fog = iris_FogParameters(iris_fogP.FogColor, 0.0, iris_fogP.FogEnvironmentalStart, iris_fogP.FogEnvironmentalEnd, 1.0 / (iris_fogP.FogEnvironmentalEnd - iris_fogP.FogEnvironmentalStart));"
         }
      );
      tree.parseAndInjectNodes(
         t,
         ASTInjectionPoint.BEFORE_DECLARATIONS,
         new String[]{
            "layout(std140) uniform iris_DynamicTransforms {\n    mat4 ModelViewMat;\n    mat4 TextureMat;\n    vec4 ColorModulator;\n    vec3 ModelOffset;\n} iris_transforms;\n",
            "layout(std140) uniform iris_Projection {\n    mat4 iris_ProjMat;\n};\n",
            "layout(std140) uniform iris_Globals {\nivec3 CameraBlockPos;\nfloat GlintAlpha;\nvec3 CameraOffset;\nfloat GameTime;\nvec2 ScreenSize;\nint MenuBlurRadius;\nint UseRgss;\n} iris_globalInfo;\n"
         }
      );
      if (parameters.type.glShaderType == ShaderType.VERTEX) {
         CommonTransformer.patchIntegerAttribute(t, tree, root, "mc_Entity", "iris_Entity", parameters.inputs.getEntityComponents());
         root.rename("gl_MultiTexCoord2", "gl_MultiTexCoord1");
         tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "in float iris_LineWidth;");
         if (parameters.inputs.hasTex() && !parameters.isClouds()) {
            root.replaceReferenceExpressions(t, "gl_MultiTexCoord0", "vec4(iris_UV0, 0.0, 1.0)");
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "in vec2 iris_UV0;");
         } else {
            root.replaceReferenceExpressions(t, "gl_MultiTexCoord0", "vec4(0.5, 0.5, 0.0, 1.0)");
         }

         if (parameters.inputs.isIE()) {
            root.replaceReferenceExpressions(t, "gl_MultiTexCoord1", "vec4(iris_LightUV, 0.0, 1.0)");
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uniform ivec2 iris_LightUV;");
         } else if (parameters.inputs.hasLight()) {
            root.replaceReferenceExpressions(t, "gl_MultiTexCoord1", "vec4(iris_UV2, 0.0, 1.0)");
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "in ivec2 iris_UV2;");
         } else {
            root.replaceReferenceExpressions(t, "gl_MultiTexCoord1", "vec4(240.0, 240.0, 0.0, 1.0)");
         }

         CommonTransformer.patchMultiTexCoord3(t, tree, root, parameters);
         CommonTransformer.replaceGlMultiTexCoordBounded(t, root, 4, 7);
      }

      if (parameters.inputs.hasColor() && parameters.type == PatchShaderType.VERTEX) {
         if (parameters.alpha.reference() == Float.MAX_VALUE) {
            root.replaceReferenceExpressions(t, "gl_Color", "vec4((iris_Color * iris_transforms.ColorModulator).rgb, iris_transforms.ColorModulator.a)");
         } else if (parameters.isClouds()) {
            root.replaceReferenceExpressions(t, "gl_Color", "iris_cloudCol");
         } else {
            root.replaceReferenceExpressions(t, "gl_Color", "(iris_Color * iris_transforms.ColorModulator)");
         }

         if (parameters.type.glShaderType == ShaderType.VERTEX) {
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "in vec4 iris_Color;");
         }
      } else if (parameters.inputs.isGlint() && !parameters.inputs.hasOverlay()) {
         root.replaceReferenceExpressions(
            t, "gl_Color", "vec4(iris_transforms.ColorModulator.rgb, iris_transforms.ColorModulator.a * iris_globalInfo.GlintAlpha)"
         );
      } else {
         root.replaceReferenceExpressions(t, "gl_Color", "iris_transforms.ColorModulator");
      }

      if (parameters.type.glShaderType == ShaderType.VERTEX && !parameters.isClouds()) {
         if (parameters.inputs.hasNormal()) {
            if (!parameters.inputs.isNewLines()) {
               root.rename("gl_Normal", "iris_Normal");
            } else {
               root.replaceReferenceExpressions(t, "gl_Normal", "vec3(0.0, 0.0, 1.0)");
            }

            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "in vec3 iris_Normal;");
         } else {
            root.replaceReferenceExpressions(t, "gl_Normal", "vec3(0.0, 0.0, 1.0)");
         }
      }

      tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS, new String[]{"uniform mat4 iris_LightmapTextureMatrix;"});
      root.replaceExpressionMatches(
         t, CommonTransformer.glTextureMatrix0, parameters.inputs.isGlint() && parameters.inputs.hasOverlay() ? "mat4(1.0)" : "iris_transforms.TextureMat"
      );
      root.replaceExpressionMatches(t, CommonTransformer.glTextureMatrix1, "iris_LightmapTextureMatrix");
      root.replaceReferenceExpressions(t, "gl_NormalMatrix", "iris_NormalMat");
      root.replaceReferenceExpressions(t, "gl_ModelViewMatrixInverse", "iris_ModelViewMatInverse");
      root.replaceReferenceExpressions(t, "gl_ProjectionMatrixInverse", "iris_ProjMatInverse");
      tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uniform mat3 iris_NormalMat;");
      tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uniform mat4 iris_ProjMatInverse;");
      tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uniform mat4 iris_ModelViewMatInverse;");
      if (parameters.type.glShaderType == ShaderType.VERTEX) {
         tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS, new String[]{"in vec3 iris_Position;"});
         if (root.identifierIndex.has("ftransform")) {
            tree.parseAndInjectNodes(
               t, ASTInjectionPoint.BEFORE_FUNCTIONS, new String[]{"vec4 ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }"}
            );
         }

         if (parameters.inputs.isNewLines()) {
            root.replaceReferenceExpressions(t, "gl_Vertex", "vec4(iris_Position + iris_vertex_offset, 1.0)");
            root.rename("main", "irisMain");
            tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS, new String[]{"vec3 iris_vertex_offset = vec3(0.0);"});
            tree.parseAndInjectNodes(
               t,
               ASTInjectionPoint.END,
               new String[]{
                  "void iris_widen_lines(vec4 linePosStart, vec4 linePosEnd) {vec3 ndc1 = linePosStart.xyz / linePosStart.w;vec3 ndc2 = linePosEnd.xyz / linePosEnd.w;vec2 lineScreenDirection = normalize((ndc2.xy - ndc1.xy) * iris_globalInfo.ScreenSize);vec2 lineOffset = vec2(-lineScreenDirection.y, lineScreenDirection.x) * iris_LineWidth / iris_globalInfo.ScreenSize;if (lineOffset.x < 0.0) {    lineOffset *= -1.0;}if (gl_VertexID % 2 == 0) {    gl_Position = vec4((ndc1 + vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);} else {    gl_Position = vec4((ndc1 - vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);}}",
                  "void main() {iris_vertex_offset = iris_Normal;irisMain();vec4 linePosEnd = gl_Position;gl_Position = vec4(0.0);iris_vertex_offset = vec3(0.0);irisMain();vec4 linePosStart = gl_Position;iris_widen_lines(linePosStart, linePosEnd);}"
               }
            );
         } else if (parameters.isClouds()) {
            tree.parseAndInjectNodes(
               t,
               ASTInjectionPoint.BEFORE_DECLARATIONS,
               new String[]{
                  "layout(std140) uniform iris_CloudInfo {\n    vec4 CloudColor;\n    vec3 CloudOffset;\n    vec3 CellSize;\n} iris_Clouds;\n",
                  "const vec3[] iris_cloudVertices = vec3[](\n    // Bottom face\n    vec3(1, 0, 0),\n    vec3(1, 0, 1),\n    vec3(0, 0, 1),\n    vec3(0, 0, 0),\n    // Top face\n    vec3(0, 1, 0),\n    vec3(0, 1, 1),\n    vec3(1, 1, 1),\n    vec3(1, 1, 0),\n    // North face\n    vec3(0, 0, 0),\n    vec3(0, 1, 0),\n    vec3(1, 1, 0),\n    vec3(1, 0, 0),\n    // South face\n    vec3(1, 0, 1),\n    vec3(1, 1, 1),\n    vec3(0, 1, 1),\n    vec3(0, 0, 1),\n    // West face\n    vec3(0, 0, 1),\n    vec3(0, 1, 1),\n    vec3(0, 1, 0),\n    vec3(0, 0, 0),\n    // East face\n    vec3(1, 0, 0),\n    vec3(1, 1, 0),\n    vec3(1, 1, 1),\n    vec3(1, 0, 1)\n);\n",
                  "const vec3[] iris_cloudNormals = vec3[](\n    // Bottom face\n    vec3(0, -1, 0),\n    // Top face\n    vec3(0, 1, 0),\n    // North face\n    vec3(0, 0, -1),\n    // South face\n    vec3(0, 0, 1),\n    // West face\n    vec3(-1, 0, 0),\n    // East face\n    vec3(1, 0, 0)\n);\n",
                  "const vec4[] iris_faceColors = vec4[](\n    // Bottom face\n    vec4(0.7, 0.7, 0.7, 0.8),\n    // Top face\n    vec4(1.0, 1.0, 1.0, 0.8),\n    // North face\n    vec4(0.8, 0.8, 0.8, 0.8),\n    // South face\n    vec4(0.8, 0.8, 0.8, 0.8),\n    // West face\n    vec4(0.9, 0.9, 0.9, 0.8),\n    // East face\n    vec4(0.9, 0.9, 0.9, 0.8)\n);\n",
                  "vec3 iris_cloudPos;",
                  "vec3 iris_cloudNormal;",
                  "vec4 iris_cloudCol;\n",
                  "const int FLAG_MASK_DIR = 7;",
                  "const int FLAG_INSIDE_FACE = 1 << 4;",
                  "const int FLAG_USE_TOP_COLOR = 1 << 5;",
                  "const int FLAG_EXTRA_Z = 1 << 6;",
                  "const int FLAG_EXTRA_X = 1 << 7;",
                  "uniform isamplerBuffer CloudFaces;",
                  "void iris_cloudsMain() {\n    int quadVertex = gl_VertexID % 4;\n    int index = (gl_VertexID / 4) * 3;\n\n    int cellX = texelFetch(CloudFaces, index).r;\n    int cellZ = texelFetch(CloudFaces, index + 1).r;\n    int dirAndFlags = texelFetch(CloudFaces, index + 2).r;\n    int direction = dirAndFlags & FLAG_MASK_DIR;\n    bool isInsideFace = (dirAndFlags & FLAG_INSIDE_FACE) == FLAG_INSIDE_FACE;\n    bool useTopColor = (dirAndFlags & FLAG_USE_TOP_COLOR) == FLAG_USE_TOP_COLOR;\n    cellX = (cellX << 1) | ((dirAndFlags & FLAG_EXTRA_X) >> 7);\n    cellZ = (cellZ << 1) | ((dirAndFlags & FLAG_EXTRA_Z) >> 6);\n    vec3 faceVertex = iris_cloudVertices[(direction * 4) + (isInsideFace ? 3 - quadVertex : quadVertex)];\n    iris_cloudPos = (faceVertex * iris_Clouds.CellSize) + (vec3(cellX, 0, cellZ) * iris_Clouds.CellSize) + iris_Clouds.CloudOffset;\n    iris_cloudNormal = iris_cloudNormals[direction];\n    iris_cloudCol = (useTopColor ? iris_faceColors[1] : iris_faceColors[direction]) * iris_Clouds.CloudColor;\n    }\n"
               }
            );
            tree.prependMainFunctionBody(t, "iris_cloudsMain();");
            root.replaceReferenceExpressions(t, "gl_Vertex", "vec4(iris_cloudPos, 1.0)");
            root.replaceReferenceExpressions(t, "gl_Normal", "iris_cloudNormal");
         } else {
            root.replaceReferenceExpressions(t, "gl_Vertex", "vec4(iris_Position, 1.0)");
         }
      }

      root.replaceReferenceExpressions(t, "gl_ModelViewProjectionMatrix", "(gl_ProjectionMatrix * gl_ModelViewMatrix)");
      StringBuilder transform = new StringBuilder("(");
      if (parameters.inputs.isNewLines()) {
         tree.parseAndInjectNodes(
            t,
            ASTInjectionPoint.BEFORE_DECLARATIONS,
            new String[]{
               "const float iris_VIEW_SHRINK = 1.0 - (1.0 / 256.0);",
               "const mat4 iris_VIEW_SCALE = mat4(iris_VIEW_SHRINK, 0.0, 0.0, 0.0,0.0, iris_VIEW_SHRINK, 0.0, 0.0,0.0, 0.0, iris_VIEW_SHRINK, 0.0,0.0, 0.0, 0.0, 1.0);"
            }
         );
         transform.append("iris_VIEW_SCALE * ");
      }

      transform.append("iris_transforms.ModelViewMat");
      if (parameters.hasChunkOffset) {
         tree.parseAndInjectNodes(
            t,
            ASTInjectionPoint.BEFORE_FUNCTIONS,
            new String[]{
               "mat4 _iris_internal_translate(vec3 offset) {return mat4(1.0, 0.0, 0.0, 0.0,0.0, 1.0, 0.0, 0.0,0.0, 0.0, 1.0, 0.0,offset.x, offset.y, offset.z, 1.0); }"
            }
         );
         transform.append(" * _iris_internal_translate(iris_transforms.ModelOffset)");
      }

      transform.append(")");
      root.replaceReferenceExpressions(t, "gl_ModelViewMatrix", transform.toString());
      CommonTransformer.injectForwardZProjection(t, tree);
      root.replaceReferenceExpressions(t, "gl_ProjectionMatrix", "iris_undoRevZ(iris_ProjMat)");
   }
}
