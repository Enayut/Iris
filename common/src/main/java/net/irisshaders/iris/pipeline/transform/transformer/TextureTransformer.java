package net.irisshaders.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinFixedTypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinFixedTypeSpecifier.BuiltinType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

public class TextureTransformer {
   public static void transform(
      ASTParser t, TranslationUnit tree, Root root, TextureStage stage, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap
   ) {
      textureMap.forEach(
         (stringTextureTypeTextureStageTri, s) -> {
            if (stringTextureTypeTextureStageTri.third() == stage) {
               String name = (String)stringTextureTypeTextureStageTri.first();

               for (Identifier id : root.identifierIndex.get(name)) {
                  TypeAndInitDeclaration initDeclaration = (TypeAndInitDeclaration)id.getAncestor(2, 0, TypeAndInitDeclaration.class::isInstance);
                  if (initDeclaration != null) {
                     DeclarationExternalDeclaration declaration = (DeclarationExternalDeclaration)initDeclaration.getAncestor(
                        1, 0, DeclarationExternalDeclaration.class::isInstance
                     );
                     if (declaration != null
                        && initDeclaration.getType().getTypeSpecifier() instanceof BuiltinFixedTypeSpecifier fixed
                        && isTypeValid((TextureType)stringTextureTypeTextureStageTri.second(), fixed.type)) {
                        root.rename((String)stringTextureTypeTextureStageTri.first(), s);
                        break;
                     }
                  }
               }
            }
         }
      );
   }

   private static boolean isTypeValid(TextureType expectedType, BuiltinType extractedType) {
      return switch (expectedType) {
         case TEXTURE_1D -> extractedType == BuiltinType.SAMPLER1D || extractedType == BuiltinType.ISAMPLER1D || extractedType == BuiltinType.USAMPLER1D;
         case TEXTURE_RECTANGLE -> extractedType == BuiltinType.SAMPLER2DRECT
            || extractedType == BuiltinType.ISAMPLER2DRECT
            || extractedType == BuiltinType.USAMPLER2DRECT;
         case TEXTURE_2D -> extractedType == BuiltinType.SAMPLER2D || extractedType == BuiltinType.ISAMPLER2D || extractedType == BuiltinType.USAMPLER2D;
         case TEXTURE_3D -> extractedType == BuiltinType.SAMPLER3D || extractedType == BuiltinType.ISAMPLER3D || extractedType == BuiltinType.USAMPLER3D;
      };
   }
}
