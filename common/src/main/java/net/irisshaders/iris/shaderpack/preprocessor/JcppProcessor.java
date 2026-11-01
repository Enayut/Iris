package net.irisshaders.iris.shaderpack.preprocessor;

import net.irisshaders.iris.helpers.StringPair;
import org.anarres.cpp.Feature;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Preprocessor;
import org.anarres.cpp.StringLexerSource;
import org.anarres.cpp.Token;

public class JcppProcessor {
   public static String glslPreprocessSource(String source, Iterable<StringPair> environmentDefines) {
      if (!source.contains("#warning IRIS_JCPP_GLSL_VERSION") && !source.contains("#warning IRIS_JCPP_GLSL_EXTENSION")) {
         source = source.replace("#version", "#warning IRIS_JCPP_GLSL_VERSION");
         source = source.replace("#extension", "#warning IRIS_JCPP_GLSL_EXTENSION");
         source = source.replace("\u0000", "");
         GlslCollectingListener listener = new GlslCollectingListener();
         Preprocessor pp = new Preprocessor();

         try {
            for (StringPair envDefine : environmentDefines) {
               pp.addMacro(envDefine.key(), envDefine.value());
            }
         } catch (LexerException e) {
            throw new RuntimeException("Unexpected LexerException processing macros", e);
         }

         pp.setListener(listener);
         pp.addInput(new StringLexerSource(source, true));
         pp.addFeature(Feature.KEEPCOMMENTS);
         StringBuilder builder = new StringBuilder();

         try {
            while (true) {
               Token tok = pp.token();
               if (tok == null || tok.getType() == 265) {
                  break;
               }

               builder.append(tok.getText());
            }
         } catch (Exception e) {
            throw new RuntimeException("GLSL source pre-processing failed", e);
         }

         builder.append("\n");
         return listener.collectLines() + builder;
      } else {
         throw new RuntimeException("Some shader author is trying to exploit internal Iris implementation details, stop!");
      }
   }
}
