package net.irisshaders.iris.shaderpack.preprocessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import org.anarres.cpp.Feature;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Preprocessor;
import org.anarres.cpp.PreprocessorCommand;
import org.anarres.cpp.StringLexerSource;
import org.anarres.cpp.Token;

public class PropertiesPreprocessor {
   public static final Pattern BACKSLASH_MATCHER = Pattern.compile(
      "(?<!\\\\\\n)^(?![ \\t]*(#|block\\.\\d*|layer\\.\\d*|item\\.\\d*|entity\\.\\d*|dimension\\.\\d*)).+", 8
   );

   public static String preprocessSource(String source, ShaderPackOptions shaderPackOptions, Iterable<StringPair> environmentDefines) {
      if (!source.contains("#warning IRIS_PASSTHROUGH ") && !source.contains("IRIS_PASSTHROUGHBACKSLASH")) {
         List<String> booleanValues = getBooleanValues(shaderPackOptions);
         Map<String, String> stringValues = getStringValues(shaderPackOptions);

         try {
            Preprocessor pp = new Preprocessor();

            String var13;
            try {
               for (String value : booleanValues) {
                  pp.addMacro(value);
               }

               for (StringPair envDefine : environmentDefines) {
                  if (envDefine.value().isEmpty()) {
                     pp.addMacro(envDefine.key());
                  } else {
                     pp.addMacro(envDefine.key(), envDefine.value());
                  }
               }

               stringValues.forEach((name, value) -> {
                  try {
                     pp.addMacro(name, value);
                  } catch (LexerException ex) {
                     Iris.logger.fatal("Failed to preprocess property file!", ex);
                  }
               });
               var13 = process(pp, source);
            } catch (Throwable var9) {
               try {
                  pp.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }

               throw var9;
            }

            pp.close();
            return var13;
         } catch (IOException e) {
            throw new RuntimeException("Unexpected IOException while processing macros", e);
         } catch (LexerException e) {
            throw new RuntimeException("Unexpected LexerException processing macros", e);
         }
      } else {
         throw new RuntimeException("Some shader author is trying to exploit internal Iris implementation details, stop!");
      }
   }

   public static String preprocessSource(String source, Iterable<StringPair> environmentDefines) {
      if (source.contains("#warning IRIS_PASSTHROUGH ")) {
         throw new RuntimeException("Some shader author is trying to exploit internal Iris implementation details, stop!");
      }

      Preprocessor preprocessor = new Preprocessor();

      try {
         for (StringPair envDefine : environmentDefines) {
            preprocessor.addMacro(envDefine.key(), envDefine.value());
         }
      } catch (LexerException e) {
         Iris.logger.fatal("Failed to preprocess property file!", e);
      }

      return process(preprocessor, source);
   }

   private static String process(Preprocessor preprocessor, String source) {
      preprocessor.setListener(new PropertiesCommentListener());
      PropertyCollectingListener listener = new PropertyCollectingListener();
      preprocessor.setListener(listener);
      source = Arrays.stream(source.split("\\R")).map(String::trim).filter(s -> !s.isBlank()).map(line -> {
         if (line.startsWith("#")) {
            for (PreprocessorCommand command : PreprocessorCommand.values()) {
               if (line.startsWith("#" + command.name().replace("PP_", "").toLowerCase(Locale.ROOT))) {
                  return (CharSequence)line;
               }
            }

            return "";
         } else {
            return line.replace("#", "");
         }
      }).collect(Collectors.joining("\n")) + "\n";
      source = source.replace("\\", "IRIS_PASSTHROUGHBACKSLASH");
      preprocessor.addInput(new StringLexerSource(source, true));
      preprocessor.addFeature(Feature.KEEPCOMMENTS);
      StringBuilder builder = new StringBuilder();

      try {
         while (true) {
            Token tok = preprocessor.token();
            if (tok == null || tok.getType() == 265) {
               break;
            }

            builder.append(tok.getText());
         }
      } catch (Exception e) {
         Iris.logger.error("Properties pre-processing failed", e);
      }

      source = builder.toString();
      return (listener.collectLines() + source).replace("IRIS_PASSTHROUGHBACKSLASH", "\\");
   }

   private static List<String> getBooleanValues(ShaderPackOptions shaderPackOptions) {
      List<String> booleanValues = new ArrayList<>();
      shaderPackOptions.getOptionSet().getBooleanOptions().forEach((string, value) -> {
         boolean trueValue = shaderPackOptions.getOptionValues().getBooleanValueOrDefault(string);
         if (trueValue) {
            booleanValues.add(string);
         }
      });
      return booleanValues;
   }

   private static Map<String, String> getStringValues(ShaderPackOptions shaderPackOptions) {
      Map<String, String> stringValues = new HashMap<>();
      shaderPackOptions.getOptionSet()
         .getStringOptions()
         .forEach((optionName, value) -> stringValues.put(optionName, shaderPackOptions.getOptionValues().getStringValueOrDefault(optionName)));
      return stringValues;
   }
}
