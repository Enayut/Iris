package net.irisshaders.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinNumericTypeSpecifier;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.util.Type;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.pipeline.transform.parameter.SodiumParameters;

public class SodiumTransformer {
   public static void transform(ASTParser t, TranslationUnit tree, Root root, SodiumParameters parameters) {
      CommonTransformer.transform(t, tree, root, parameters, false);
      replaceMidTexCoord(t, tree, root, 3.0517578E-5F);
      replaceMCEntity(t, tree, root);
      CommonTransformer.replaceMidBlock(t, tree, root, parameters);
      boolean needsNormal = root.identifierIndex.has("gl_Normal") || root.identifierIndex.has("at_tangent");
      root.replaceExpressionMatches(t, CommonTransformer.glTextureMatrix0, "mat4(1.0)");
      root.replaceExpressionMatches(t, CommonTransformer.glTextureMatrix1, "iris_LightmapTextureMatrix");
      tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_FUNCTIONS, "uniform mat4 iris_LightmapTextureMatrix;");
      root.replaceReferenceExpressions(t, "gl_ProjectionMatrix", "iris_undoRevZ(u_ProjectionMatrix)");
      if (parameters.type.glShaderType == ShaderType.VERTEX) {
         root.rename("gl_MultiTexCoord2", "gl_MultiTexCoord1");
         root.replaceReferenceExpressions(t, "gl_MultiTexCoord0", "vec4((_vert_tex_diffuse_coord_bias * u_TexCoordShrink) + _vert_tex_diffuse_coord, 0.0, 1.0)");
         root.replaceReferenceExpressions(t, "gl_MultiTexCoord1", "vec4(_vert_tex_light_coord, 0.0, 1.0)");
         CommonTransformer.patchMultiTexCoord3(t, tree, root, parameters);
         CommonTransformer.replaceGlMultiTexCoordBounded(t, root, 4, 7);
      }

      root.rename("gl_Color", "_vert_color");
      if (parameters.type.glShaderType == ShaderType.VERTEX && needsNormal) {
         root.rename("gl_Normal", "irs_Normal");
         root.replaceReferenceExpressions(t, "at_tangent", "irs_Tangent");
      }

      root.replaceReferenceExpressions(t, "gl_NormalMatrix", "iris_NormalMat");
      tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uniform mat3 iris_NormalMat;");
      tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uniform mat4 iris_ModelViewMatInverse;");
      tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uniform mat4 iris_ProjMatInverse;");
      root.rename("gl_ModelViewMatrix", "u_ModelViewMatrix");
      root.rename("gl_ModelViewMatrixInverse", "iris_ModelViewMatInverse");
      root.rename("gl_ProjectionMatrixInverse", "iris_ProjMatInverse");
      if (parameters.type.glShaderType == ShaderType.VERTEX) {
         if (root.identifierIndex.has("ftransform")) {
            tree.parseAndInjectNodes(
               t, ASTInjectionPoint.BEFORE_FUNCTIONS, new String[]{"vec4 ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }"}
            );
         }

         tree.parseAndInjectNodes(
            t,
            ASTInjectionPoint.BEFORE_DECLARATIONS,
            new String[]{"vec4 getVertexPosition() { return vec4(_vert_position + iris_RegionOffset + _get_draw_translation(_draw_id), 1.0); }"}
         );
         root.replaceReferenceExpressions(t, "gl_Vertex", "getVertexPosition()");
         injectVertInit(t, tree, root, parameters, needsNormal);
      }

      tree.parseAndInjectNode(
         t,
         ASTInjectionPoint.BEFORE_DECLARATIONS,
         "layout(std140) uniform u_Globals {\n      mat4 u_ProjectionMatrix;\n      mat4 u_ModelViewMatrix;\n\n      vec4 u_FogColor;\n      vec2 u_EnvironmentFog;\n      vec2 u_RenderFog;\n\n      vec2 u_TexelSize;\n      vec2 u_TexCoordShrink;\n\n      float u_FadePeriodInv;\n      bool u_UseRGSS;\n  };"
      );
      root.replaceReferenceExpressions(t, "gl_ModelViewProjectionMatrix", "(iris_undoRevZ(u_ProjectionMatrix) * u_ModelViewMatrix)");
      CommonTransformer.injectForwardZProjection(t, tree);
      CommonTransformer.applyIntelHd4000Workaround(root);
   }

   public static void injectVertInit(ASTParser t, TranslationUnit tree, Root root, SodiumParameters parameters, boolean needsNormal) {
      String chunkFadeDeclaration = parameters.shadow ? "const float mc_chunkFade = -1.0;" : "float mc_chunkFade;";
      String chunkFadeSetup = parameters.shadow
         ? ""
         : "int chunkId = int(_draw_id);int chunkFade = texelFetch(u_SectionTimeInfo, int((iris_RegionID * 256u) + uint(chunkId))).r;float fade = clamp(float(iris_CurrentTime - chunkFade) * u_FadePeriodInv, 0.0, 1.0);mc_chunkFade = (chunkFade < 0) ? 1.0 : fade;";
      tree.parseAndInjectNodes(
         t,
         ASTInjectionPoint.BEFORE_DECLARATIONS,
         new String[]{
            "layout(std140) uniform iris_SodiumPushConstants {\n    vec3 iris_RegionOffset;\n    int iris_CurrentTime;\n    uint iris_RegionID;\n};\n",
            "vec3 _vert_position;",
            "vec2 _vert_tex_diffuse_coord;",
            "vec2 _vert_tex_diffuse_coord_bias;",
            "vec2 _vert_tex_light_coord;",
            "vec4 _vert_color;",
            "const uint POSITION_BITS        = 20u;",
            "const uint POSITION_MAX_COORD   = 1u << POSITION_BITS;",
            "const uint TANGENT_W_BIT   = 16u;",
            "const uint POSITION_MAX_VALUE   = POSITION_MAX_COORD - 1u;",
            "const uint TEXTURE_BITS         = 15u;",
            "const uint TEXTURE_MAX_COORD    = 1u << TEXTURE_BITS;",
            "const uint TEXTURE_MAX_VALUE    = TEXTURE_MAX_COORD - 1u;",
            "const float VERTEX_SCALE = 32.0 / POSITION_MAX_COORD;",
            "const float VERTEX_OFFSET = -8.0;",
            chunkFadeDeclaration,
            "uint _draw_id;",
            "vec3 irs_Normal;",
            "vec4 irs_Tangent;",
            "const uint MATERIAL_USE_MIP_OFFSET = 0u;",
            "vec2 signNotZero(vec2 v) {\n    return vec2(v.x >= 0.0 ? 1.0 : -1.0,\n                v.y >= 0.0 ? 1.0 : -1.0);\n}\n",
            "vec3 decodeOct24(uint packe) {\n    int sx = int(packe);\n    vec2 e = vec2((sx << 8) >> 20, (sx << 20) >> 20) * (1.0 / 2047.0);\n\n    vec3 v = vec3(e, 1.0 - abs(e.x) - abs(e.y));\n\n    v.xy = (v.z >= 0.0) ? v.xy : (1.0 - abs(v.yx)) * signNotZero(v.xy);\n\n    return normalize(v);\n}\n",
            "void onb_from_normal(in vec3 n, out vec3 t1, out vec3 t2) {\n    float s = n.z >= 0.0f ? 1.0f : -1.0f;\n    float a = -1.0f / (s + n.z);\n    float b = n.x * n.y * a;\n    t1 = vec3(1.0f + s * n.x * n.x * a, s * b, -s * n.x);\n    t2 = vec3(b, s + n.y * n.y * a, -n.y);\n}\n",
            "vec2 decode_diamond(float p) {\n    float x = 1.0f - 4.0f * abs(p - 0.5f);\n    float y = (p >= 0.5f ? 1.0f : -1.0f) * (1.0f - abs(x));\n    return vec2(x, y);\n}\n",
            "vec4 decode_diamond_tangent_with_sign(vec3 normal, int qByte, bool signPositive) {\n    vec3 t1, t2;\n    onb_from_normal(normal, t1, t2);\n    float d = float(qByte) / 256.0;\n    vec2 p = decode_diamond(d);\n    vec3 tang = normalize(p.x * t1 + p.y * t2);\n    return vec4(tang, signPositive ? 1.0 : -1.0);\n}\n",
            "\tuvec3 _deinterleave_u20x3(uvec2 data) {\n\t     uvec3 hi = (uvec3(data.x) >> uvec3(0u, 10u, 20u)) & 0x3FFu;\n\t     uvec3 lo = (uvec3(data.y) >> uvec3(0u, 10u, 20u)) & 0x3FFu;\n\n\t     return (hi << 10u) | lo;\n\t }\n\t",
            "\tvec2 _get_texcoord() {\n\t     return vec2(a_TexCoord & TEXTURE_MAX_VALUE) / float(TEXTURE_MAX_COORD);\n\t }\n",
            "\tvec2 _get_texcoord_bias() {\n\t\t\t\t\t\treturn mix(vec2(-1.0), vec2(1.0), bvec2(a_TexCoord >> TEXTURE_BITS));\n\t\t\t\t}\n",
            "float _material_mip_bias(uint material) {\n    return ((material >> MATERIAL_USE_MIP_OFFSET) & 1u) != 0u ? 0.0f : -4.0f;\n}",
            "uniform isamplerBuffer u_SectionTimeInfo;",
            "void _vert_init() {_vert_position = ((_deinterleave_u20x3(a_Position) * VERTEX_SCALE) + VERTEX_OFFSET);_vert_tex_diffuse_coord = _get_texcoord();_vert_tex_diffuse_coord_bias = _get_texcoord_bias();_vert_tex_light_coord = vec2(a_LightAndData.xy);_vert_color = a_Color;"
               + (needsNormal ? "irs_Normal = decodeOct24(iris_Normal);" : "")
               + (needsNormal ? "irs_Tangent = decode_diamond_tangent_with_sign(irs_Normal, (int(iris_Normal >> 24u)), (a_LightAndData.z & 1u) != 0u);" : "")
               + "_draw_id = a_LightAndData[3];"
               + chunkFadeSetup
               + "}",
            "uvec3 _get_relative_chunk_coord(uint pos) {\n    // Packing scheme is defined by LocalSectionIndex\n    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);\n}",
            "vec3 _get_draw_translation(uint pos) {\n    return _get_relative_chunk_coord(pos) * vec3(16.0f);\n}\n"
         }
      );
      CommonTransformer.addIfNotExists(root, t, tree, "a_Position", Type.U32VEC2, StorageType.IN);
      CommonTransformer.addIfNotExists(root, t, tree, "a_TexCoord", Type.U32VEC2, StorageType.IN);
      CommonTransformer.addIfNotExists(root, t, tree, "a_Color", Type.F32VEC4, StorageType.IN);
      CommonTransformer.addIfNotExists(root, t, tree, "a_LightAndData", Type.U32VEC4, StorageType.IN);
      if (needsNormal) {
         CommonTransformer.addIfNotExists(root, t, tree, "iris_Normal", Type.UINT32, StorageType.IN);
      }

      tree.prependMainFunctionBody(t, "_vert_init();");
   }

   public static void replaceMCEntity(ASTParser t, TranslationUnit tree, Root root) {
      Type dimension = Type.BOOL;

      for (Identifier id : root.identifierIndex.get("mc_Entity")) {
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

      root.replaceReferenceExpressions(t, "mc_Entity", "iris_Entity");
      switch (dimension) {
         case BOOL:
            return;
         case FLOAT32:
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "float iris_Entity = int(mc_Entity >> 1u) - 1;");
            break;
         case F32VEC2:
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "vec2 iris_Entity = vec2(int(mc_Entity >> 1u) - 1, mc_Entity & 1u);");
            break;
         case F32VEC3:
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "vec3 iris_Entity = vec3(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0);");
            break;
         case F32VEC4:
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "vec4 iris_Entity = vec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0, 1.0);");
            break;
         case INT32:
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uint iris_Entity = int(mc_Entity >> 1u) - 1;");
            break;
         case I32VEC2:
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "ivec2 iris_Entity = ivec2(int(mc_Entity >> 1u) - 1, mc_Entity & 1u);");
            break;
         case I32VEC3:
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "ivec3 iris_Entity = ivec3(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0);");
            break;
         case I32VEC4:
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "ivec4 iris_Entity = ivec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0, 1);");
            break;
         default:
            throw new IllegalStateException("Got an invalid format mc_Entity (" + dimension.getCompactName() + ").");
      }

      tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "in uint mc_Entity;");
   }

   public static void replaceMidTexCoord(ASTParser t, TranslationUnit tree, Root root, float textureScale) {
      Type dimension = Type.BOOL;

      for (Identifier id : root.identifierIndex.get("mc_midTexCoord")) {
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

      root.replaceReferenceExpressions(t, "mc_midTexCoord", "iris_MidTex");
      switch (dimension) {
         case BOOL:
            return;
         case FLOAT32:
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "float iris_MidTex = (float(mc_midTexCoord.x) * " + textureScale + ");");
            break;
         case F32VEC2:
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "vec2 iris_MidTex = (vec2(mc_midTexCoord.xy) * " + textureScale + ").xy;");
            break;
         case F32VEC3:
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "vec3 iris_MidTex = vec3(vec2(mc_midTexCoord.xy) * " + textureScale + ", 0.0);");
            break;
         case F32VEC4:
            tree.parseAndInjectNode(
               t, ASTInjectionPoint.BEFORE_DECLARATIONS, "vec4 iris_MidTex = vec4(vec2(mc_midTexCoord.xy) * " + textureScale + ", 0.0, 1.0);"
            );
            break;
         default:
            throw new IllegalStateException("Somehow got a midTexCoord that is *above* 4 dimensions???");
      }

      tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "in uvec2 mc_midTexCoord;");
   }
}
