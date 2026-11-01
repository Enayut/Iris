package net.irisshaders.iris.shaderpack.materialmap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.irisshaders.iris.Iris;
import org.jetbrains.annotations.NotNull;

public record BlockEntry(NamespacedId id, Map<String, String> propertyPredicates) implements Entry {
   @NotNull
   public static Entry parse(@NotNull String entry) {
      if (entry.isEmpty()) {
         throw new IllegalArgumentException("Called BlockEntry::parse with an empty string");
      }

      if (entry.startsWith("%")) {
         entry = entry.replace("%", "");
         String[] splitStates = entry.split(":");
         if (splitStates.length == 1) {
            return new TagEntry(new NamespacedId("minecraft", entry), Map.of());
         }

         if (splitStates.length == 2 && !splitStates[1].contains("=")) {
            return new TagEntry(new NamespacedId(splitStates[0], splitStates[1]), Map.of());
         }

         int statesStart;
         NamespacedId id;
         if (splitStates[1].contains("=")) {
            statesStart = 1;
            id = new NamespacedId("minecraft", splitStates[0]);
         } else {
            statesStart = 2;
            id = new NamespacedId(splitStates[0], splitStates[1]);
         }

         Map<String, String> map = new HashMap<>();

         for (int index = statesStart; index < splitStates.length; index++) {
            String[] propertyParts = splitStates[index].split("=");
            if (propertyParts.length != 2) {
               Iris.logger.warn("Warning: the block ID map entry \"" + entry + "\" could not be fully parsed:");
               Iris.logger.warn("- Block state property filters must be of the form \"key=value\", but " + splitStates[index] + " is not of that form!");
            } else {
               map.put(propertyParts[0], propertyParts[1]);
            }
         }

         return new TagEntry(id, map);
      } else {
         String[] splitStates = entry.split(":");
         if (splitStates.length == 1) {
            return new BlockEntry(new NamespacedId("minecraft", entry), Collections.emptyMap());
         }

         if (splitStates.length == 2 && !splitStates[1].contains("=")) {
            return new BlockEntry(new NamespacedId(splitStates[0], splitStates[1]), Collections.emptyMap());
         }

         int statesStart;
         NamespacedId id;
         if (splitStates[1].contains("=")) {
            statesStart = 1;
            id = new NamespacedId("minecraft", splitStates[0]);
         } else {
            statesStart = 2;
            id = new NamespacedId(splitStates[0], splitStates[1]);
         }

         Map<String, String> map = new HashMap<>();

         for (int index = statesStart; index < splitStates.length; index++) {
            String[] propertyParts = splitStates[index].split("=");
            if (propertyParts.length != 2) {
               Iris.logger.warn("Warning: the block ID map entry \"" + entry + "\" could not be fully parsed:");
               Iris.logger.warn("- Block state property filters must be of the form \"key=value\", but " + splitStates[index] + " is not of that form!");
            } else {
               map.put(propertyParts[0], propertyParts[1]);
            }
         }

         return new BlockEntry(id, map);
      }
   }
}
