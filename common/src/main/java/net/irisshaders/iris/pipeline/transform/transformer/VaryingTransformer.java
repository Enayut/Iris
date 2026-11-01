package net.irisshaders.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.declaration.VariableDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.Expression;
import io.github.douira.glsl_transformer.ast.node.expression.SequenceExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.FunctionCallExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.GroupingExpression;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.LayoutQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.NamedLayoutQualifierPart;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.util.Type;
import io.github.douira.glsl_transformer.util.Type.NumberType;
import java.util.Map;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.parameter.GeometryInfoParameters;

public final class VaryingTransformer {
   private VaryingTransformer() {
   }

   public static void add(ASTParser parser, Map<PatchShaderType, TranslationUnit> trees, Type type, String name) {
      add(parser, trees, type, name, requiresFlat(type), "0");
   }

   public static void add(ASTParser parser, Map<PatchShaderType, TranslationUnit> trees, Type type, String name, boolean flat, String geometryVertexIndex) {
      trees.forEach(
         (stage, tree) -> {
            if (tree != null) {
               tree.getRoot()
                  .indexBuildSession(
                     () -> add(
                        parser,
                        tree,
                        stage,
                        trees.get(PatchShaderType.GEOMETRY) != null,
                        trees.get(PatchShaderType.TESS_EVAL) != null,
                        trees.get(PatchShaderType.TESS_CONTROL) != null,
                        type,
                        name,
                        flat,
                        geometryVertexIndex
                     )
                  );
            }
         }
      );
   }

   public static void add(ASTParser parser, TranslationUnit tree, GeometryInfoParameters parameters, Type type, String name) {
      add(parser, tree, parameters, type, name, requiresFlat(type), "0");
   }

   public static void add(
      ASTParser parser, TranslationUnit tree, GeometryInfoParameters parameters, Type type, String name, boolean flat, String geometryVertexIndex
   ) {
      add(parser, tree, parameters.type, parameters.hasGeometry, parameters.hasTesselation, parameters.hasTesselation, type, name, flat, geometryVertexIndex);
   }

   private static boolean requiresFlat(Type type) {
      return type.getNumberType() != NumberType.FLOATING_POINT || type.getBitDepth() == 64;
   }

   private static void add(
      ASTParser parser,
      TranslationUnit tree,
      PatchShaderType stage,
      boolean hasGeometry,
      boolean hasTessellation,
      boolean hasTessellationControl,
      Type type,
      String name,
      boolean flat,
      String geometryVertexIndex
   ) {
      Root root = tree.getRoot();
      String declaration = !flat && !requiresFlat(type) ? "" : "flat ";
      String glslType = type.getMostCompactName();
      switch (stage) {
         case VERTEX:
            tree.parseAndInjectNode(parser, ASTInjectionPoint.BEFORE_DECLARATIONS, declaration + "out " + glslType + " " + name + ";");
            break;
         case TESS_CONTROL: {
            String output = name + "TCS";
            root.replaceReferenceExpressions(parser, name, name + "[gl_InvocationID]");
            tree.parseAndInjectNodes(
               parser,
               ASTInjectionPoint.BEFORE_DECLARATIONS,
               new String[]{declaration + "in " + glslType + " " + name + "[];", declaration + "out " + glslType + " " + output + "[];"}
            );
            tree.prependMainFunctionBody(parser, output + "[gl_InvocationID] = " + name + "[gl_InvocationID];");
            break;
         }
         case TESS_EVAL: {
            String input = name + (hasTessellationControl ? "TCS" : "");
            String output = name + "TES";
            String value = !flat && !requiresFlat(type) ? interpolate(tree, input) : input + "[0]";
            root.rename(name, output);
            tree.parseAndInjectNodes(
               parser,
               ASTInjectionPoint.BEFORE_DECLARATIONS,
               new String[]{declaration + "in " + glslType + " " + input + "[];", declaration + "out " + glslType + " " + output + ";"}
            );
            tree.prependMainFunctionBody(parser, output + " = " + value + ";");
            break;
         }
         case GEOMETRY: {
            String input = name + (hasTessellation ? "TES" : "");
            String output = name + "GS";
            String value = input + "[" + geometryVertexIndex + "]";
            root.replaceReferenceExpressions(parser, name, value);
            tree.parseAndInjectNodes(
               parser,
               ASTInjectionPoint.BEFORE_DECLARATIONS,
               new String[]{declaration + "in " + glslType + " " + input + "[];", declaration + "out " + glslType + " " + output + ";"}
            );
            root.process(root.nodeIndex.getStream(FunctionCallExpression.class).filter(call -> call.getFunctionName() != null).filter(call -> {
               return switch (call.getFunctionName().getName()) {
                  case "EmitVertex" -> call.getParameters().isEmpty();
                  case "EmitStreamVertex" -> call.getParameters().size() == 1;
                  default -> false;
               };
            }), call -> {
               GroupingExpression wrapper = (GroupingExpression)parser.parseExpression(root, "(" + output + " = " + value + ", 0)");
               call.replaceBy(wrapper);
               ((Expression)((SequenceExpression)wrapper.getOperand()).getExpressions().get(1)).replaceByAndDelete(call);
            });
            break;
         }
         case FRAGMENT: {
            String input = name + (hasGeometry ? "GS" : (hasTessellation ? "TES" : ""));
            root.rename(name, input);
            tree.parseAndInjectNode(parser, ASTInjectionPoint.BEFORE_DECLARATIONS, declaration + "in " + glslType + " " + input + ";");
            break;
         }
         default:
            throw new IllegalArgumentException("Cannot add a varying to " + stage);
      }
   }

   private static String interpolate(TranslationUnit tree, String input) {
      String domain = tree.getRoot()
         .nodeIndex
         .getStream(VariableDeclaration.class)
         .map(declaration -> declaration.getTypeQualifier().getParts())
         .filter(parts -> parts.stream().anyMatch(part -> part instanceof StorageQualifier storage && storage.storageType == StorageType.IN))
         .flatMap(parts -> parts.stream().filter(LayoutQualifier.class::isInstance).map(LayoutQualifier.class::cast))
         .flatMap(layout -> layout.getParts().stream())
         .filter(NamedLayoutQualifierPart.class::isInstance)
         .map(NamedLayoutQualifierPart.class::cast)
         .map(part -> part.getName().getName())
         .filter(name -> name.equals("triangles") || name.equals("quads") || name.equals("isolines"))
         .findFirst()
         .orElseThrow(() -> new IllegalArgumentException("Missing tessellation evaluation domain"));

      return switch (domain) {
         case "triangles" -> input + "[0] * gl_TessCoord.x + " + input + "[1] * gl_TessCoord.y + " + input + "[2] * gl_TessCoord.z";
         case "quads" -> "("
            + input
            + "[0] * (1.0 - gl_TessCoord.x) + "
            + input
            + "[1] * gl_TessCoord.x) * (1.0 - gl_TessCoord.y) + ("
            + input
            + "[2] * (1.0 - gl_TessCoord.x) + "
            + input
            + "[3] * gl_TessCoord.x) * gl_TessCoord.y";
         default -> input + "[0] * (1.0 - gl_TessCoord.x) + " + input + "[1] * gl_TessCoord.x";
      };
   }
}
