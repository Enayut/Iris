package net.irisshaders.iris.shaderpack.preprocessor;

import org.anarres.cpp.DefaultPreprocessorListener;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Source;

public class PropertiesCommentListener extends DefaultPreprocessorListener {
   private static final String UNKNOWN_PREPROCESSOR_DIRECTIVE = "Unknown preprocessor directive";
   private static final String NOT_A_WORD = "Preprocessor directive not a word";

   public void handleError(Source source, int line, int column, String msg) throws LexerException {
      if (!msg.contains("Unknown preprocessor directive") && !msg.contains("Preprocessor directive not a word")) {
         super.handleError(source, line, column, msg);
      }
   }
}
