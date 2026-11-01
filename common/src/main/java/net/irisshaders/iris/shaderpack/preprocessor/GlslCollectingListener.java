package net.irisshaders.iris.shaderpack.preprocessor;

import org.anarres.cpp.DefaultPreprocessorListener;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Source;

public class GlslCollectingListener extends DefaultPreprocessorListener {
   public static final String VERSION_MARKER = "#warning IRIS_JCPP_GLSL_VERSION";
   public static final String EXTENSION_MARKER = "#warning IRIS_JCPP_GLSL_EXTENSION";
   private final StringBuilder builder = new StringBuilder();

   public void handleWarning(Source source, int line, int column, String msg) throws LexerException {
      if (msg.startsWith("#warning IRIS_JCPP_GLSL_VERSION")) {
         this.builder.append(msg.replace("#warning IRIS_JCPP_GLSL_VERSION", "#version "));
         this.builder.append('\n');
      } else if (msg.startsWith("#warning IRIS_JCPP_GLSL_EXTENSION")) {
         this.builder.append(msg.replace("#warning IRIS_JCPP_GLSL_EXTENSION", "#extension "));
         this.builder.append('\n');
      } else {
         super.handleWarning(source, line, column, msg);
      }
   }

   public String collectLines() {
      return this.builder.toString();
   }
}
