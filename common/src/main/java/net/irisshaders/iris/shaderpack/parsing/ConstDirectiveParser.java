package net.irisshaders.iris.shaderpack.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConstDirectiveParser {
   public static List<ConstDirectiveParser.ConstDirective> findDirectives(String source) {
      List<ConstDirectiveParser.ConstDirective> directives = new ArrayList<>();

      for (String line : source.split("\\R")) {
         findDirectiveInLine(line).ifPresent(directives::add);
      }

      return directives;
   }

   public static Optional<ConstDirectiveParser.ConstDirective> findDirectiveInLine(String line) {
      if (line.contains("const") && line.contains("=") && line.contains(";")) {
         line = line.trim();
         if (!line.startsWith("const")) {
            return Optional.empty();
         }

         line = line.substring("const".length());
         if (!startsWithWhitespace(line)) {
            return Optional.empty();
         }

         line = line.trim();
         ConstDirectiveParser.Type type;
         if (line.startsWith("int")) {
            type = ConstDirectiveParser.Type.INT;
            line = line.substring("int".length());
         } else if (line.startsWith("float")) {
            type = ConstDirectiveParser.Type.FLOAT;
            line = line.substring("float".length());
         } else if (line.startsWith("vec2")) {
            type = ConstDirectiveParser.Type.VEC2;
            line = line.substring("vec2".length());
         } else if (line.startsWith("ivec3")) {
            type = ConstDirectiveParser.Type.IVEC3;
            line = line.substring("ivec3".length());
         } else if (line.startsWith("vec4")) {
            type = ConstDirectiveParser.Type.VEC4;
            line = line.substring("vec4".length());
         } else {
            if (!line.startsWith("bool")) {
               return Optional.empty();
            }

            type = ConstDirectiveParser.Type.BOOL;
            line = line.substring("bool".length());
         }

         if (!startsWithWhitespace(line)) {
            return Optional.empty();
         }

         int equalsIndex = line.indexOf(61);
         if (equalsIndex == -1) {
            return Optional.empty();
         }

         String key = line.substring(0, equalsIndex).trim();
         if (!isWord(key)) {
            return Optional.empty();
         }

         String remaining = line.substring(equalsIndex + 1);
         int semicolonIndex = remaining.indexOf(59);
         if (semicolonIndex == -1) {
            return Optional.empty();
         }

         String value = remaining.substring(0, semicolonIndex).trim();
         return Optional.of(new ConstDirectiveParser.ConstDirective(type, key, value));
      } else {
         return Optional.empty();
      }
   }

   private static boolean startsWithWhitespace(String text) {
      return !text.isEmpty() && Character.isWhitespace(text.charAt(0));
   }

   private static boolean isWord(String text) {
      if (text.isEmpty()) {
         return false;
      }

      for (char character : text.toCharArray()) {
         if (!Character.isDigit(character) && !Character.isAlphabetic(character) && character != '_') {
            return false;
         }
      }

      return true;
   }

   public static class ConstDirective {
      private final ConstDirectiveParser.Type type;
      private final String key;
      private final String value;

      ConstDirective(ConstDirectiveParser.Type type, String key, String value) {
         this.type = type;
         this.key = key;
         this.value = value;
      }

      public ConstDirectiveParser.Type getType() {
         return this.type;
      }

      public String getKey() {
         return this.key;
      }

      public String getValue() {
         return this.value;
      }

      @Override
      public String toString() {
         return "ConstDirective { " + this.type + " " + this.key + " = " + this.value + "; }";
      }
   }

   public enum Type {
      INT,
      FLOAT,
      VEC2,
      IVEC3,
      VEC4,
      BOOL;
   }
}
