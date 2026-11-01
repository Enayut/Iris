package net.irisshaders.iris.shaderpack;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.pipeline.transform.ShaderPrinter;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.shaderpack.materialmap.BlockEntry;
import net.irisshaders.iris.shaderpack.materialmap.BlockRenderType;
import net.irisshaders.iris.shaderpack.materialmap.Entry;
import net.irisshaders.iris.shaderpack.materialmap.LegacyIdMap;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.TagEntry;
import net.irisshaders.iris.shaderpack.option.OrderBackedProperties;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.preprocessor.PropertiesPreprocessor;

public class IdMap {
   private final Object2IntMap<NamespacedId> itemIdMap;
   private final Object2IntMap<NamespacedId> entityIdMap;
   private final Int2ObjectLinkedOpenHashMap<List<TagEntry>> blockTagMap;
   private Int2ObjectLinkedOpenHashMap<List<BlockEntry>> blockPropertiesMap;
   private Map<NamespacedId, BlockRenderType> blockRenderTypeMap;

   IdMap(Path shaderPath, ShaderPackOptions shaderPackOptions, Iterable<StringPair> environmentDefines) {
      this.itemIdMap = loadProperties(shaderPath, "item.properties", shaderPackOptions, environmentDefines)
         .map(IdMap::parseItemIdMap)
         .orElse(Object2IntMaps.emptyMap());
      this.entityIdMap = loadProperties(shaderPath, "entity.properties", shaderPackOptions, environmentDefines)
         .map(IdMap::parseEntityIdMap)
         .orElse(Object2IntMaps.emptyMap());
      this.blockTagMap = new Int2ObjectLinkedOpenHashMap();
      loadProperties(shaderPath, "block.properties", shaderPackOptions, environmentDefines).ifPresent(blockProperties -> {
         this.blockPropertiesMap = parseBlockMap(blockProperties, "block.", "block.properties", this.blockTagMap);
         this.blockRenderTypeMap = parseRenderTypeMap(blockProperties, "layer.", "block.properties");
      });
      if (this.blockPropertiesMap == null) {
         this.blockPropertiesMap = new Int2ObjectLinkedOpenHashMap();
         LegacyIdMap.addLegacyValues(this.blockPropertiesMap);
      }

      if (this.blockRenderTypeMap == null) {
         this.blockRenderTypeMap = Collections.emptyMap();
      }
   }

   private static Optional<Properties> loadProperties(
      Path shaderPath, String name, ShaderPackOptions shaderPackOptions, Iterable<StringPair> environmentDefines
   ) {
      String fileContents = readProperties(shaderPath, name);
      if (fileContents == null) {
         return Optional.empty();
      }

      String processed = PropertiesPreprocessor.preprocessSource(fileContents, shaderPackOptions, environmentDefines)
         .replaceAll("\\\\\\n\\s*\\n", " ")
         .replaceAll("\\S *block\\.", "\nblock.");
      StringReader propertiesReader = new StringReader(processed);
      warnMissingBackslashInPropertiesFile(processed, name);
      Properties properties = new OrderBackedProperties();

      try {
         properties.load(propertiesReader);
      } catch (IOException e) {
         Iris.logger.error("Error loading " + name + " at " + shaderPath, e);
         return Optional.empty();
      }

      if (Iris.getIrisConfig().areDebugOptionsEnabled()) {
         ShaderPrinter.deleteIfClearing();

         try (OutputStream os = Files.newOutputStream(IrisPlatformHelpers.getInstance().getGameDir().resolve("patched_shaders").resolve(name))) {
            properties.store(new OutputStreamWriter(os, StandardCharsets.UTF_8), "Patched version of properties");
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }

      return Optional.of(properties);
   }

   private static String readProperties(Path shaderPath, String name) {
      try {
         return Files.readString(shaderPath.resolve(name), StandardCharsets.ISO_8859_1);
      } catch (NoSuchFileException e) {
         Iris.logger.debug("An " + name + " file was not found in the current shaderpack");
         return null;
      } catch (IOException e) {
         Iris.logger.error("An IOException occurred reading " + name + " from the current shaderpack", e);
         return null;
      }
   }

   private static Object2IntMap<NamespacedId> parseItemIdMap(Properties properties) {
      return parseIdMap(properties, "item.", "item.properties");
   }

   private static Object2IntMap<NamespacedId> parseEntityIdMap(Properties properties) {
      return parseIdMap(properties, "entity.", "entity.properties");
   }

   private static Object2IntMap<NamespacedId> parseIdMap(Properties properties, String keyPrefix, String fileName) {
      Object2IntMap<NamespacedId> idMap = new Object2IntOpenHashMap();
      idMap.defaultReturnValue(-1);
      IdMap.DuplicateTracker duplicateTracker = new IdMap.DuplicateTracker(fileName, "entry");
      properties.forEach(
         (keyObject, valueObject) -> {
            String key = (String)keyObject;
            String value = (String)valueObject;
            if (key.startsWith(keyPrefix)) {
               int intId;
               try {
                  intId = Integer.parseInt(key.substring(keyPrefix.length()));
               } catch (NumberFormatException e) {
                  Iris.logger.warn("Failed to parse line in " + fileName + ": invalid key " + key);
                  return;
               }

               for (String part : value.split("\\s+")) {
                  if (part.contains("=")) {
                     Iris.logger
                        .warn(
                           "Failed to parse an Identifier in " + fileName + " for the key " + key + ": state properties are currently not supported: " + part
                        );
                  } else {
                     duplicateTracker.checkAndRecord(part, key, part);
                     idMap.put(new NamespacedId(part), intId);
                  }
               }
            }
         }
      );
      duplicateTracker.reportSummary();
      return Object2IntMaps.unmodifiable(idMap);
   }

   private static Int2ObjectLinkedOpenHashMap<List<BlockEntry>> parseBlockMap(
      Properties properties, String keyPrefix, String fileName, Int2ObjectLinkedOpenHashMap<List<TagEntry>> blockTagMap
   ) {
      Int2ObjectLinkedOpenHashMap<List<BlockEntry>> blockEntriesById = new Int2ObjectLinkedOpenHashMap();
      Int2ObjectLinkedOpenHashMap<List<TagEntry>> tagEntriesById = new Int2ObjectLinkedOpenHashMap();
      IdMap.DuplicateTracker duplicateTracker = new IdMap.DuplicateTracker(fileName, "block entry");
      properties.forEach((keyObject, valueObject) -> {
         String key = (String)keyObject;
         String value = (String)valueObject;
         if (key.startsWith(keyPrefix)) {
            int intId;
            try {
               intId = Integer.parseInt(key.substring(keyPrefix.length()));
            } catch (NumberFormatException e) {
               Iris.logger.warn("Failed to parse line in " + fileName + ": invalid key " + key);
               return;
            }

            List<BlockEntry> blockEntries = new ArrayList<>();
            List<TagEntry> tagEntries = new ArrayList<>();

            for (String part : value.split("\\s+")) {
               if (!part.isEmpty()) {
                  try {
                     Entry entry = BlockEntry.parse(part);
                     if (entry instanceof BlockEntry be) {
                        String uniqueBlockIdentifier = IdMap.DuplicateTracker.getUniqueBlockIdentifier(be);
                        duplicateTracker.checkAndRecord(uniqueBlockIdentifier, key, part);
                        blockEntries.add(be);
                     } else if (entry instanceof TagEntry te) {
                        tagEntries.add(te);
                     }
                  } catch (Exception e) {
                     Iris.logger.warn("Unexpected error while parsing an entry from " + fileName + " for the key " + key + ":", e);
                  }
               }
            }

            blockEntriesById.put(intId, Collections.unmodifiableList(blockEntries));
            tagEntriesById.put(intId, Collections.unmodifiableList(tagEntries));
         }
      });
      duplicateTracker.reportSummary();
      blockTagMap.putAll(tagEntriesById);
      return blockEntriesById;
   }

   private static Map<NamespacedId, BlockRenderType> parseRenderTypeMap(Properties properties, String keyPrefix, String fileName) {
      Map<NamespacedId, BlockRenderType> overrides = new HashMap<>();
      IdMap.DuplicateTracker duplicateTracker = new IdMap.DuplicateTracker(fileName, "render type map");
      properties.forEach((keyObject, valueObject) -> {
         String key = (String)keyObject;
         String value = (String)valueObject;
         if (key.startsWith(keyPrefix)) {
            String keyWithoutPrefix = key.substring(keyPrefix.length());
            BlockRenderType renderType = BlockRenderType.fromString(keyWithoutPrefix).orElse(null);
            if (renderType == null) {
               Iris.logger.warn("Failed to parse line in " + fileName + ": invalid block render type: " + key);
            } else {
               for (String part : value.split("\\s+")) {
                  if (part.startsWith("%")) {
                     Iris.logger.fatal("Cannot use a tag in the render type map: " + key + " = " + value);
                  } else {
                     duplicateTracker.checkAndRecord(part, key, part);
                     overrides.put(new NamespacedId(part), renderType);
                  }
               }
            }
         }
      });
      duplicateTracker.reportSummary();
      return overrides;
   }

   private static Map<NamespacedId, String> parseDimensionMap(Properties properties, String keyPrefix, String fileName) {
      Map<NamespacedId, String> overrides = new Object2ObjectArrayMap();
      properties.forEach((keyObject, valueObject) -> {
         String key = (String)keyObject;
         String value = (String)valueObject;
         if (key.startsWith(keyPrefix)) {
            key = key.substring(keyPrefix.length());

            for (String part : value.split("\\s+")) {
               overrides.put(new NamespacedId(part), key);
            }
         }
      });
      return overrides;
   }

   private static void warnMissingBackslashInPropertiesFile(String processedSource, String propertiesFileName) {
      if (!propertiesFileName.equals("shaders.properties")) {
         String[] fileNameSections = propertiesFileName.split("\\.");
         String entryName = "entry";
         if (fileNameSections.length >= 2) {
            entryName = fileNameSections[0] + " entry";
         }

         Matcher matcher = PropertiesPreprocessor.BACKSLASH_MATCHER.matcher(processedSource);

         while (matcher.find()) {
            Iris.logger.warn("Found missing \"\\\" in file \"{}\" in {}: \"{}\"", propertiesFileName, entryName, matcher.group(0));

            for (int i = 1; i <= matcher.groupCount(); i++) {
               String match = matcher.group(i);
               if (match != null) {
                  Iris.logger.warn("At ID: \"{}\"", match);
               }
            }
         }
      }
   }

   public Int2ObjectLinkedOpenHashMap<List<BlockEntry>> getBlockProperties() {
      return this.blockPropertiesMap;
   }

   public Int2ObjectLinkedOpenHashMap<List<TagEntry>> getTagEntries() {
      return this.blockTagMap;
   }

   public Object2IntFunction<NamespacedId> getItemIdMap() {
      return this.itemIdMap;
   }

   public Object2IntFunction<NamespacedId> getEntityIdMap() {
      return this.entityIdMap;
   }

   public Map<NamespacedId, BlockRenderType> getBlockRenderTypeMap() {
      return this.blockRenderTypeMap;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         IdMap idMap = (IdMap)o;
         return Objects.equals(this.itemIdMap, idMap.itemIdMap)
            && Objects.equals(this.entityIdMap, idMap.entityIdMap)
            && Objects.equals(this.blockPropertiesMap, idMap.blockPropertiesMap)
            && Objects.equals(this.blockTagMap, idMap.blockTagMap)
            && Objects.equals(this.blockRenderTypeMap, idMap.blockRenderTypeMap);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.itemIdMap, this.entityIdMap, this.blockPropertiesMap, this.blockTagMap, this.blockRenderTypeMap);
   }

   private static class DuplicateTracker {
      private final Map<String, String> identifierToKeyMap = new HashMap<>();
      private final Set<String> reportedDuplicates = new HashSet<>();
      private final Map<String, Integer> duplicateCounts = new HashMap<>();
      private final String fileName;
      private final String itemType;
      private final boolean debugEnabled;

      public DuplicateTracker(String fileName, String itemType) {
         this.fileName = fileName;
         this.itemType = itemType;
         this.debugEnabled = Iris.getIrisConfig().areDebugOptionsEnabled();
      }

      public static String getUniqueBlockIdentifier(BlockEntry entry) {
         StringBuilder sb = new StringBuilder();
         sb.append(entry.id().toString());
         Map<String, String> properties = entry.propertyPredicates();
         if (properties != null && !properties.isEmpty()) {
            List<String> propertyKeys = new ArrayList<>(properties.keySet());
            Collections.sort(propertyKeys);

            for (String propKey : propertyKeys) {
               sb.append(':').append(propKey).append('=').append(properties.get(propKey));
            }
         }

         return sb.toString();
      }

      public void checkAndRecord(String identifier, String key, String displayValue) {
         if (this.identifierToKeyMap.containsKey(identifier)) {
            String previousKey = this.identifierToKeyMap.get(identifier);
            String duplicateKey = identifier + "|" + previousKey + "|" + key;
            this.duplicateCounts.put(duplicateKey, this.duplicateCounts.getOrDefault(duplicateKey, 0) + 1);
            if (this.debugEnabled && !this.reportedDuplicates.contains(duplicateKey)) {
               Iris.logger.warn("Duplicate {} in {}: '{}' in '{}' and '{}'", this.itemType, this.fileName, displayValue, previousKey, key);
               this.reportedDuplicates.add(duplicateKey);
            } else {
               this.reportedDuplicates.add(duplicateKey);
            }
         }

         this.identifierToKeyMap.put(identifier, key);
      }

      public void reportSummary() {
         if (this.debugEnabled && !this.duplicateCounts.isEmpty()) {
            int totalDuplicates = this.duplicateCounts.values().stream().mapToInt(Integer::intValue).sum();
            Iris.logger.warn("Found {} duplicate {} in {}.", totalDuplicates, this.itemType + (totalDuplicates > 1 ? "s" : ""), this.fileName);
         }
      }
   }
}
