package net.irisshaders.iris.shaderpack.option;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.irisshaders.iris.helpers.OptionalBoolean;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import net.irisshaders.iris.shaderpack.parsing.ParsedString;
import net.irisshaders.iris.shaderpack.transform.line.LineTransform;

public final class OptionAnnotatedSource {
   private static final ImmutableSet<String> VALID_CONST_OPTION_NAMES;
   private final ImmutableList<String> lines;
   private final ImmutableMap<Integer, BooleanOption> booleanOptions;
   private final ImmutableMap<Integer, StringOption> stringOptions;
   private final ImmutableMap<Integer, String> diagnostics;
   private final ImmutableMap<String, IntList> booleanDefineReferences;

   public OptionAnnotatedSource(String source) {
      this(ImmutableList.copyOf(source.split("\\R")));
   }

   public OptionAnnotatedSource(ImmutableList<String> lines) {
      this.lines = lines;
      OptionAnnotatedSource.AnnotationsBuilder builder = new OptionAnnotatedSource.AnnotationsBuilder();

      for (int index = 0; index < lines.size(); index++) {
         String line = (String)lines.get(index);
         parseLine(builder, index, line);
      }

      this.booleanOptions = builder.booleanOptions.build();
      this.stringOptions = builder.stringOptions.build();
      this.diagnostics = builder.diagnostics.build();
      this.booleanDefineReferences = ImmutableMap.copyOf(builder.booleanDefineReferences);
   }

   private static void parseLine(OptionAnnotatedSource.AnnotationsBuilder builder, int index, String lineText) {
      if (lineText.contains("#define") || lineText.contains("const") || lineText.contains("#ifdef") || lineText.contains("#ifndef")) {
         ParsedString line = new ParsedString(lineText.trim());
         if (line.takeLiteral("#ifdef") || line.takeLiteral("#ifndef")) {
            parseIfdef(builder, index, line);
         } else if (line.takeLiteral("const")) {
            parseConst(builder, index, line);
         } else if (line.currentlyContains("#define")) {
            parseDefineOption(builder, index, line);
         }
      }
   }

   private static void parseIfdef(OptionAnnotatedSource.AnnotationsBuilder builder, int index, ParsedString line) {
      if (line.takeSomeWhitespace()) {
         String name = line.takeWord();
         line.takeSomeWhitespace();
         if (name != null && line.isEnd()) {
            builder.booleanDefineReferences.computeIfAbsent(name, n -> new IntArrayList()).add(index);
         }
      }
   }

   private static void parseConst(OptionAnnotatedSource.AnnotationsBuilder builder, int index, ParsedString line) {
      if (!line.takeSomeWhitespace()) {
         builder.diagnostics.put(index, "Expected whitespace after const and before type declaration");
      } else {
         boolean isString;
         if (!line.takeLiteral("int") && !line.takeLiteral("float")) {
            if (!line.takeLiteral("bool")) {
               builder.diagnostics
                  .put(
                     index,
                     "Unexpected type declaration after const. Expected int, float, or bool. Vector const declarations cannot be configured using shader options."
                  );
               return;
            }

            isString = false;
         } else {
            isString = true;
         }

         if (!line.takeSomeWhitespace()) {
            builder.diagnostics.put(index, "Expected whitespace after type declaration.");
         } else {
            String name = line.takeWord();
            if (name == null) {
               builder.diagnostics.put(index, "Expected name of option after type declaration, but an unexpected character was detected first.");
            } else {
               line.takeSomeWhitespace();
               if (!line.takeLiteral("=")) {
                  builder.diagnostics.put(index, "Unexpected characters before equals sign in const declaration.");
               } else {
                  line.takeSomeWhitespace();
                  String value = line.takeWordOrNumber();
                  if (value == null) {
                     builder.diagnostics.put(index, "Unexpected non-whitespace characters after equals sign");
                  } else {
                     line.takeSomeWhitespace();
                     if (!line.takeLiteral(";")) {
                        builder.diagnostics.put(index, "Value between the equals sign and the semicolon wasn't parsed as a valid word or number.");
                     } else {
                        line.takeSomeWhitespace();
                        String comment;
                        if (line.takeComments()) {
                           comment = line.takeRest().trim();
                        } else {
                           if (!line.isEnd()) {
                              builder.diagnostics.put(index, "Unexpected non-whitespace characters outside of comment after semicolon");
                              return;
                           }

                           comment = null;
                        }

                        if (!isString) {
                           boolean booleanValue;
                           if ("true".equals(value)) {
                              booleanValue = true;
                           } else {
                              if (!"false".equals(value)) {
                                 builder.diagnostics.put(index, "Expected true or false as the value of a boolean const option, but got " + value + ".");
                                 return;
                              }

                              booleanValue = false;
                           }

                           if (!VALID_CONST_OPTION_NAMES.contains(name)) {
                              builder.diagnostics
                                 .put(
                                    index,
                                    "This was a valid const boolean option declaration, but "
                                       + name
                                       + " was not recognized as being a name of one of the configurable const options."
                                 );
                           } else {
                              builder.booleanOptions.put(index, new BooleanOption(OptionType.CONST, name, comment, booleanValue));
                           }
                        } else if (!VALID_CONST_OPTION_NAMES.contains(name)) {
                           builder.diagnostics
                              .put(
                                 index,
                                 "This was a valid const option declaration, but "
                                    + name
                                    + " was not recognized as being a name of one of the configurable const options."
                              );
                        } else {
                           StringOption option = StringOption.create(OptionType.CONST, name, comment, value);
                           if (option != null) {
                              builder.stringOptions.put(index, option);
                           } else {
                              builder.diagnostics
                                 .put(
                                    index,
                                    "Ignoring this const option because it is missing an allowed values listin a comment, but is not a boolean const option."
                                 );
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void parseDefineOption(OptionAnnotatedSource.AnnotationsBuilder builder, int index, ParsedString line) {
      boolean hasLeadingComment = line.takeComments();
      line.takeSomeWhitespace();
      if (!line.takeLiteral("#define")) {
         builder.diagnostics.put(index, "This line contains an occurrence of \"#define\" but it wasn't in a place we expected, ignoring it.");
      } else if (!line.takeSomeWhitespace()) {
         builder.diagnostics.put(index, "This line properly starts with a #define statement but doesn't have any whitespace characters after the #define.");
      } else {
         String name = line.takeWord();
         if (name == null) {
            builder.diagnostics.put(index, "Invalid syntax after #define directive. No alphanumeric or underscore characters detected.");
         } else {
            boolean tookWhitespace = line.takeSomeWhitespace();
            if (line.isEnd()) {
               builder.booleanOptions.put(index, new BooleanOption(OptionType.DEFINE, name, null, !hasLeadingComment));
            } else if (line.takeComments()) {
               String comment = line.takeRest().trim();
               builder.booleanOptions.put(index, new BooleanOption(OptionType.DEFINE, name, comment, !hasLeadingComment));
            } else if (!tookWhitespace) {
               builder.diagnostics
                  .put(index, "Invalid syntax after #define directive. Only alphanumeric or underscore characters are allowed in option names.");
            } else if (hasLeadingComment) {
               builder.diagnostics
                  .put(
                     index,
                     "Ignoring potential non-boolean #define option since it has a leading comment. Leading comments (//) are only allowed on boolean #define options."
                  );
            } else {
               String value = line.takeWordOrNumber();
               if (value == null) {
                  builder.diagnostics
                     .put(
                        index,
                        "Ignoring this #define directive because it doesn't appear to be a boolean #define, and its potential value wasn't a valid number or a valid word."
                     );
               } else {
                  tookWhitespace = line.takeSomeWhitespace();
                  if (line.isEnd()) {
                     builder.diagnostics
                        .put(
                           index,
                           "Ignoring this #define because it doesn't have a comment containing a list of allowed values afterwards, but it has a value so is therefore not a boolean."
                        );
                  } else {
                     if (!tookWhitespace) {
                        if (!line.takeComments()) {
                           builder.diagnostics.put(index, "Invalid syntax after value #define directive. Invalid characters after number or word.");
                           return;
                        }
                     } else if (!line.takeComments()) {
                        builder.diagnostics.put(index, "Invalid syntax after value #define directive. Only comments may come after the value.");
                        return;
                     }

                     String comment = line.takeRest().trim();
                     StringOption option = StringOption.create(OptionType.DEFINE, name, comment, value);
                     if (option == null) {
                        builder.diagnostics
                           .put(index, "Ignoring this #define because it is missing an allowed values listin a comment, but is not a boolean define.");
                     } else {
                        builder.stringOptions.put(index, option);
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean hasLeadingComment(String line) {
      return line.trim().startsWith("//");
   }

   private static String removeLeadingComment(String line) {
      ParsedString parsed = new ParsedString(line);
      parsed.takeSomeWhitespace();
      parsed.takeComments();
      return parsed.takeRest();
   }

   private static String setBooleanDefineValue(String line, OptionalBoolean newValue, boolean defaultValue) {
      if (hasLeadingComment(line) && newValue.orElse(defaultValue)) {
         return removeLeadingComment(line);
      } else {
         return !newValue.orElse(defaultValue) ? "//" + line : line;
      }
   }

   public ImmutableMap<Integer, BooleanOption> getBooleanOptions() {
      return this.booleanOptions;
   }

   public ImmutableMap<Integer, StringOption> getStringOptions() {
      return this.stringOptions;
   }

   public ImmutableMap<Integer, String> getDiagnostics() {
      return this.diagnostics;
   }

   public ImmutableMap<String, IntList> getBooleanDefineReferences() {
      return this.booleanDefineReferences;
   }

   public OptionSet getOptionSet(AbsolutePackPath filePath, Set<String> booleanDefineReferences) {
      OptionSet.Builder builder = OptionSet.builder();
      this.booleanOptions.forEach((lineIndex, option) -> {
         if (booleanDefineReferences.contains(option.getName())) {
            OptionLocation location = new OptionLocation(filePath, lineIndex);
            builder.addBooleanOption(location, option);
         }
      });
      this.stringOptions.forEach((lineIndex, option) -> {
         OptionLocation location = new OptionLocation(filePath, lineIndex);
         builder.addStringOption(location, option);
      });
      return builder.build();
   }

   public LineTransform asTransform(OptionValues values) {
      return (index, line) -> this.edit(values, index, line);
   }

   public String apply(OptionValues values) {
      StringBuilder source = new StringBuilder();

      for (int index = 0; index < this.lines.size(); index++) {
         source.append(this.edit(values, index, (String)this.lines.get(index)));
         source.append('\n');
      }

      return source.toString();
   }

   private String edit(OptionValues values, int index, String existing) {
      BooleanOption booleanOption = (BooleanOption)this.booleanOptions.get(index);
      if (booleanOption != null) {
         OptionalBoolean value = values.getBooleanValue(booleanOption.getName());
         if (booleanOption.getType() == OptionType.DEFINE) {
            return setBooleanDefineValue(existing, value, booleanOption.getDefaultValue());
         } else if (booleanOption.getType() == OptionType.CONST) {
            return value != OptionalBoolean.DEFAULT
               ? this.editConst(existing, Boolean.toString(booleanOption.getDefaultValue()), Boolean.toString(value.orElse(booleanOption.getDefaultValue())))
               : existing;
         } else {
            throw new AssertionError("Unknown option type " + booleanOption.getType());
         }
      } else {
         StringOption stringOption = (StringOption)this.stringOptions.get(index);
         return stringOption != null ? values.getStringValue(stringOption.getName()).map(value -> {
            if (stringOption.getType() == OptionType.DEFINE) {
               return "#define " + stringOption.getName() + " " + value + " // OptionAnnotatedSource: Changed option";
            } else if (stringOption.getType() == OptionType.CONST) {
               return this.editConst(existing, stringOption.getDefaultValue(), value);
            } else {
               throw new AssertionError("Unknown option type " + stringOption.getType());
            }
         }).orElse(existing) : existing;
      }
   }

   private String editConst(String line, String currentValue, String newValue) {
      int equalsIndex = line.indexOf(61);
      if (equalsIndex == -1) {
         throw new IllegalStateException();
      }

      String firstPart = line.substring(0, equalsIndex);
      String secondPart = line.substring(equalsIndex);
      secondPart = secondPart.replaceFirst(Pattern.quote(currentValue), Matcher.quoteReplacement(newValue));
      return firstPart + secondPart;
   }

   static {
      Builder<String> values = ImmutableSet.<String>builder()
         .add(
            "shadowMapResolution", "shadowDistance", "voxelDistance", "shadowDistanceRenderMul", "entityShadowDistanceMul", "shadowIntervalSize", "generateShadowMipmap", "generateShadowColorMipmap", "shadowHardwareFiltering", "shadowtex0Mipmap", "shadowtexMipmap", "shadowtex1Mipmap", "shadowtex0Nearest", "shadowtexNearest", "shadow0MinMagNearest", "shadowtex1Nearest", "shadow1MinMagNearest", "wetnessHalflife", "drynessHalflife", "eyeBrightnessHalflife", "centerDepthHalflife", "sunPathRotation", "ambientOcclusionLevel", "superSamplingLevel", "noiseTextureResolution"
         );

      for (int i = 0; i < 8; i++) {
         values.add("shadowcolor" + i + "Mipmap");
         values.add("shadowColor" + i + "Mipmap");
         values.add("shadowcolor" + i + "Nearest");
         values.add("shadowColor" + i + "Nearest");
         values.add("shadowcolor" + i + "MinMagNearest");
         values.add("shadowColor" + i + "MinMagNearest");
         values.add("shadowHardwareFiltering" + i);
      }

      VALID_CONST_OPTION_NAMES = values.build();
   }

   private static class AnnotationsBuilder {
      private final com.google.common.collect.ImmutableMap.Builder<Integer, BooleanOption> booleanOptions = ImmutableMap.builder();
      private final com.google.common.collect.ImmutableMap.Builder<Integer, StringOption> stringOptions = ImmutableMap.builder();
      private final com.google.common.collect.ImmutableMap.Builder<Integer, String> diagnostics = ImmutableMap.builder();
      private final Map<String, IntList> booleanDefineReferences = new HashMap<>();
   }
}
