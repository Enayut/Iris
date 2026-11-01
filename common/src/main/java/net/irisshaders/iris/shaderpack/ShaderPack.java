package net.irisshaders.iris.shaderpack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.buffer.BuiltShaderStorageInfo;
import net.irisshaders.iris.gl.buffer.ShaderStorageInfo;
import net.irisshaders.iris.gl.texture.TextureDefinition;
import net.irisshaders.iris.gui.FeatureMissingErrorScreen;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import net.irisshaders.iris.shaderpack.error.RusticError;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.include.IncludeProcessor;
import net.irisshaders.iris.shaderpack.include.ShaderPackSourceNames;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.option.OrderBackedProperties;
import net.irisshaders.iris.shaderpack.option.ProfileSet;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuContainer;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import net.irisshaders.iris.shaderpack.parsing.BooleanParser;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.shaderpack.preprocessor.PropertiesPreprocessor;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSetInterface;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import net.irisshaders.iris.shaderpack.texture.CustomTextureData;
import net.irisshaders.iris.shaderpack.texture.TextureFilteringData;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.Nullable;

public class ShaderPack {
   private static final Gson GSON = new Gson();
   public final CustomUniforms.Builder customUniforms;
   private final ProgramSet base;
   private final Map<NamespacedId, ProgramSetInterface> overrides;
   private final IdMap idMap;
   private final LanguageMap languageMap;
   private final EnumMap<TextureStage, Object2ObjectMap<String, CustomTextureData>> customTextureDataMap = new EnumMap<>(TextureStage.class);
   private final Object2ObjectMap<String, CustomTextureData> irisCustomTextureDataMap = new Object2ObjectOpenHashMap();
   private final CustomTextureData customNoiseTexture;
   private final ShaderPackOptions shaderPackOptions;
   private final OptionMenuContainer menuContainer;
   private final ProfileSet.ProfileResult profile;
   private final String profileInfo;
   private final List<ImageInformation> irisCustomImages;
   private final Set<FeatureFlags> activeFeatures;
   private final Function<AbsolutePackPath, String> sourceProvider;
   private final ShaderProperties shaderProperties;
   private final List<String> dimensionIds;
   private final Int2ObjectArrayMap<BuiltShaderStorageInfo> bufferObjects;
   private Map<NamespacedId, String> dimensionMap;

   public ShaderPack(Path root, ImmutableList<StringPair> environmentDefines, boolean isZip) throws IOException, IllegalStateException {
      this(root, Collections.emptyMap(), environmentDefines, isZip);
   }

   public ShaderPack(Path root, Map<String, String> changedConfigs, ImmutableList<StringPair> environmentDefines, boolean isZip) throws IOException, IllegalStateException {
      Objects.requireNonNull(root);
      ArrayList<StringPair> envDefines1 = new ArrayList<>(environmentDefines);
      envDefines1.addAll(IrisDefines.createIrisReplacements());
      environmentDefines = ImmutableList.copyOf(envDefines1);
      Builder<AbsolutePackPath> starts = ImmutableList.builder();
      ImmutableList<String> potentialFileNames = ShaderPackSourceNames.POTENTIAL_STARTS;
      ShaderPackSourceNames.findPresentSources(starts, root, AbsolutePackPath.fromAbsolutePath("/"), potentialFileNames);
      this.dimensionIds = new ArrayList<>();
      this.bufferObjects = new Int2ObjectArrayMap();
      boolean[] hasDimensionIds = new boolean[]{false};
      List<String> dimensionIdCreator = loadProperties(root, "dimension.properties", environmentDefines).map(dimensionProperties -> {
         hasDimensionIds[0] = !dimensionProperties.isEmpty();
         this.dimensionMap = parseDimensionMap(dimensionProperties, "dimension.", "dimension.properties");
         return this.parseDimensionIds(dimensionProperties, "dimension.");
      }).orElse(new ArrayList<>());
      if (!hasDimensionIds[0]) {
         this.dimensionMap = new Object2ObjectArrayMap();
         if (Files.exists(root.resolve("world0"))) {
            dimensionIdCreator.add("world0");
            this.dimensionMap.putIfAbsent(DimensionId.OVERWORLD, "world0");
            this.dimensionMap.putIfAbsent(new NamespacedId("*", "*"), "world0");
         }

         if (Files.exists(root.resolve("world-1"))) {
            dimensionIdCreator.add("world-1");
            this.dimensionMap.putIfAbsent(DimensionId.NETHER, "world-1");
         }

         if (Files.exists(root.resolve("world1"))) {
            dimensionIdCreator.add("world1");
            this.dimensionMap.putIfAbsent(DimensionId.END, "world1");
         }
      }

      for (String id : dimensionIdCreator) {
         if (ShaderPackSourceNames.findPresentSources(starts, root, AbsolutePackPath.fromAbsolutePath("/" + id), potentialFileNames)) {
            this.dimensionIds.add(id);
         }
      }

      IncludeGraph graph = new IncludeGraph(root, starts.build(), isZip);
      if (!graph.getFailures().isEmpty()) {
         throw new IOException(String.join("\n", graph.getFailures().values().stream().map(RusticError::toString).toArray(String[]::new)));
      }

      this.languageMap = new LanguageMap(root.resolve("lang"));
      this.shaderPackOptions = new ShaderPackOptions(graph, changedConfigs);
      graph = this.shaderPackOptions.getIncludes();
      List<StringPair> finalEnvironmentDefines = new ArrayList<>(List.copyOf(environmentDefines));

      for (FeatureFlags flag : FeatureFlags.values()) {
         if (flag.isUsable()) {
            finalEnvironmentDefines.add(new StringPair("IRIS_FEATURE_" + flag.name(), ""));
         }
      }

      this.shaderProperties = loadProperties(root, "shaders.properties")
         .map(source -> new ShaderProperties(source, this.shaderPackOptions, finalEnvironmentDefines))
         .orElseGet(ShaderProperties::empty);
      ObjectIterator var27 = this.shaderProperties.getBufferObjects().int2ObjectEntrySet().iterator();

      while (var27.hasNext()) {
         Entry<ShaderStorageInfo> shaderStorageInfoEntry = (Entry<ShaderStorageInfo>)var27.next();
         ShaderStorageInfo info = (ShaderStorageInfo)shaderStorageInfoEntry.getValue();
         if (info.name() == null) {
            this.bufferObjects
               .put(shaderStorageInfoEntry.getIntKey(), new BuiltShaderStorageInfo(info.size(), info.relative(), info.scaleX(), info.scaleY(), null));
         } else {
            String path = info.name();

            try {
               if (path.startsWith("/")) {
                  path = path.substring(1);
               }

               byte[] data = Files.readAllBytes(root.resolve(path));
               if (data.length > info.size()) {
                  throw new IllegalStateException("Tried to load a shader storage file with no space in the buffer! Increase the buffer size.");
               }

               this.bufferObjects
                  .put(shaderStorageInfoEntry.getIntKey(), new BuiltShaderStorageInfo(info.size(), info.relative(), info.scaleX(), info.scaleY(), data));
            } catch (IOException e) {
               Iris.logger.error("Shader storage buffer with index " + shaderStorageInfoEntry.getIntKey() + " and path " + path + " could not be read.", e);
            }
         }
      }

      this.activeFeatures = new HashSet<>();

      for (int i = 0; i < this.shaderProperties.getRequiredFeatureFlags().size(); i++) {
         this.activeFeatures.add(FeatureFlags.getValue(this.shaderProperties.getRequiredFeatureFlags().get(i)));
      }

      for (int i = 0; i < this.shaderProperties.getOptionalFeatureFlags().size(); i++) {
         this.activeFeatures.add(FeatureFlags.getValue(this.shaderProperties.getOptionalFeatureFlags().get(i)));
      }

      if (!this.activeFeatures.contains(FeatureFlags.SSBO) && !this.shaderProperties.getBufferObjects().isEmpty()) {
         throw new IllegalStateException(
            "An SSBO is being used, but the feature flag for SSBO's hasn't been set! Please set either a requirement or check for the SSBO feature using \"iris.features.required/optional = ssbo\"."
         );
      }

      if (!this.activeFeatures.contains(FeatureFlags.CUSTOM_IMAGES) && !this.shaderProperties.getIrisCustomImages().isEmpty()) {
         throw new IllegalStateException(
            "Custom images are being used, but the feature flag for custom images hasn't been set! Please set either a requirement or check for custom images' feature flag using \"iris.features.required/optional = CUSTOM_IMAGES\"."
         );
      }

      List<FeatureFlags> invalidFlagList = this.shaderProperties
         .getRequiredFeatureFlags()
         .stream()
         .filter(FeatureFlags::isInvalid)
         .map(FeatureFlags::getValue)
         .collect(Collectors.toList());
      List<String> invalidFeatureFlags = invalidFlagList.stream().map(FeatureFlags::getHumanReadableName).toList();
      if (!invalidFeatureFlags.isEmpty()) {
         if (Minecraft.getInstance().gui.screen() instanceof ShaderPackScreen) {
            MutableComponent component = Component.translatable(
               "iris.unsupported.pack.description",
               new Object[]{FeatureFlags.getInvalidStatus(invalidFlagList), invalidFeatureFlags.stream().collect(Collectors.joining(", ", ": ", "."))}
            );
            if (SystemUtils.IS_OS_MAC) {
               component = component.append(Component.translatable("iris.unsupported.pack.macos"));
            }

            Minecraft.getInstance()
               .gui
               .setScreen(new FeatureMissingErrorScreen(Minecraft.getInstance().gui.screen(), Component.translatable("iris.unsupported.pack"), component));
         }

         IrisApi.getInstance().getConfig().setShadersEnabledAndApply(false);
      }

      List<StringPair> newEnvDefines = new ArrayList<>(environmentDefines);
      if (this.shaderProperties.supportsColorCorrection().orElse(false)) {
         for (ColorSpace space : ColorSpace.values()) {
            newEnvDefines.add(new StringPair("COLOR_SPACE_" + space.name(), String.valueOf(space.ordinal())));
         }
      }

      List<String> optionalFeatureFlags = this.shaderProperties.getOptionalFeatureFlags().stream().filter(flag -> !FeatureFlags.isInvalid(flag)).toList();
      if (!optionalFeatureFlags.isEmpty()) {
         optionalFeatureFlags.forEach(flag -> newEnvDefines.add(new StringPair("IRIS_FEATURE_" + flag, "")));
      }

      environmentDefines = ImmutableList.copyOf(newEnvDefines);
      ProfileSet profiles = ProfileSet.fromTree(this.shaderProperties.getProfiles(), this.shaderPackOptions.getOptionSet());
      this.profile = profiles.scan(this.shaderPackOptions.getOptionSet(), this.shaderPackOptions.getOptionValues());
      List<String> disabledPrograms = new ArrayList<>();
      this.profile.current.ifPresent(profile -> disabledPrograms.addAll(profile.disabledPrograms));
      this.shaderProperties.getConditionallyEnabledPrograms().forEach((program, shaderOption) -> {
         if (!BooleanParser.parse(shaderOption, this.shaderPackOptions.getOptionValues())) {
            disabledPrograms.add(program);
         }
      });
      this.menuContainer = new OptionMenuContainer(this.shaderProperties, this.shaderPackOptions, profiles);
      String profileName = this.getCurrentProfileName();
      OptionValues profileOptions = new MutableOptionValues(
         this.shaderPackOptions.getOptionSet(), this.profile.current.map(p -> p.optionValues).orElseGet(HashMap::new)
      );
      int userOptionsChanged = this.shaderPackOptions.getOptionValues().getOptionsChanged() - profileOptions.getOptionsChanged();
      this.profileInfo = "Profile: " + profileName + " (+" + userOptionsChanged + " option" + (userOptionsChanged == 1 ? "" : "s") + " changed by user)";
      Iris.logger.info(this.profileInfo);
      IncludeProcessor includeProcessor = new IncludeProcessor(graph);
      Iterable<StringPair> finalEnvironmentDefines1 = environmentDefines;
      this.sourceProvider = path -> {
         String pathString = path.getPathString();
         String programString = pathString.substring(pathString.indexOf("/") == 0 ? 1 : 0, pathString.lastIndexOf("."));
         if (disabledPrograms.contains(programString)) {
            return null;
         }

         ImmutableList<String> lines = includeProcessor.getIncludedFile(path);
         if (lines == null) {
            return null;
         }

         StringBuilder builder = new StringBuilder();
         Iterator i$ = lines.iterator();

         while (i$.hasNext()) {
            String line = (String)i$.next();
            builder.append(line);
            builder.append('\n');
         }

         String source = builder.toString();
         return JcppProcessor.glslPreprocessSource(source, finalEnvironmentDefines1);
      };
      this.base = new ProgramSet(
         AbsolutePackPath.fromAbsolutePath("/" + this.dimensionMap.getOrDefault(new NamespacedId("*", "*"), "")),
         this.sourceProvider,
         this.shaderProperties,
         this
      );
      this.overrides = new HashMap<>();
      this.idMap = new IdMap(root, this.shaderPackOptions, environmentDefines);
      this.customNoiseTexture = this.shaderProperties.getNoiseTexturePath().map(path -> {
         try {
            return this.readTexture(root, new TextureDefinition.PNGDefinition(path));
         } catch (IOException e) {
            Iris.logger.error("Unable to read the custom noise texture at " + path, e);
            return null;
         }
      }).orElse(null);
      this.shaderProperties.getCustomTextures().forEach((textureStage, customTexturePropertiesMap) -> {
         Object2ObjectMap<String, CustomTextureData> innerCustomTextureDataMap = new Object2ObjectOpenHashMap();
         customTexturePropertiesMap.forEach((samplerName, path) -> {
            try {
               innerCustomTextureDataMap.put(samplerName, this.readTexture(root, path));
            } catch (IOException e) {
               Iris.logger.error("Unable to read the custom texture at " + path, e);
            }
         });
         this.customTextureDataMap.put(textureStage, innerCustomTextureDataMap);
      });
      this.irisCustomImages = this.shaderProperties.getIrisCustomImages();
      this.customUniforms = this.shaderProperties.getCustomUniforms();
      this.shaderProperties.getIrisCustomTextures().forEach((name, texture) -> {
         try {
            this.irisCustomTextureDataMap.put(name, this.readTexture(root, texture));
         } catch (IOException e) {
            Iris.logger.error("Unable to read the custom texture at " + texture.getName(), e);
         }
      });
   }

   private static Optional<Properties> loadProperties(Path shaderPath, String name, Iterable<StringPair> environmentDefines) {
      String fileContents = readProperties(shaderPath, name);
      if (fileContents == null) {
         return Optional.empty();
      }

      String processed = PropertiesPreprocessor.preprocessSource(fileContents, environmentDefines);
      StringReader propertiesReader = new StringReader(processed);
      Properties properties = new OrderBackedProperties();

      try {
         properties.load(propertiesReader);
      } catch (IOException e) {
         Iris.logger.error("Error loading " + name + " at " + shaderPath, e);
         return Optional.empty();
      }

      return Optional.of(properties);
   }

   private static Map<NamespacedId, String> parseDimensionMap(Properties properties, String keyPrefix, String fileName) {
      Map<NamespacedId, String> overrides = new Object2ObjectArrayMap();
      properties.forEach((keyObject, valueObject) -> {
         String key = (String)keyObject;
         String value = (String)valueObject;
         if (key.startsWith(keyPrefix)) {
            key = key.substring(keyPrefix.length());

            for (String part : value.split("\\s+")) {
               if (part.equals("*")) {
                  overrides.put(new NamespacedId("*", "*"), key);
               }

               overrides.put(new NamespacedId(part), key);
            }
         }
      });
      return overrides;
   }

   @Nullable
   private static ProgramSet loadOverrides(
      boolean has, AbsolutePackPath path, Function<AbsolutePackPath, String> sourceProvider, ShaderProperties shaderProperties, ShaderPack pack
   ) {
      return has ? new ProgramSet(path, sourceProvider, shaderProperties, pack) : null;
   }

   private static Optional<String> loadProperties(Path shaderPath, String name) {
      String fileContents = readProperties(shaderPath, name);
      return fileContents == null ? Optional.empty() : Optional.of(fileContents);
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

   private List<String> parseDimensionIds(Properties dimensionProperties, String keyPrefix) {
      List<String> names = new ArrayList<>();
      dimensionProperties.forEach((keyObject, value) -> {
         String key = (String)keyObject;
         if (key.startsWith(keyPrefix)) {
            key = key.substring(keyPrefix.length());
            names.add(key);
         }
      });
      return names;
   }

   private String getCurrentProfileName() {
      return this.profile.current.<String>map(p -> p.name).orElse("Custom");
   }

   public String getProfileInfo() {
      return this.profileInfo;
   }

   public CustomTextureData readTexture(Path root, TextureDefinition definition) throws IOException {
      String path = definition.getName();
      CustomTextureData customTextureData;
      if (path.contains(":")) {
         String[] parts = path.split(":");
         if (parts.length > 2) {
            Iris.logger.warn("Resource location " + path + " contained more than two parts?");
         }

         if (!parts[0].equals("minecraft") || !parts[1].equals("dynamic/lightmap_1") && !parts[1].equals("dynamic/light_map_1")) {
            customTextureData = new CustomTextureData.ResourceData(parts[0], parts[1]);
         } else {
            customTextureData = new CustomTextureData.LightmapMarker();
         }
      } else {
         if (path.startsWith("/")) {
            path = path.substring(1);
         }

         boolean blur = definition instanceof TextureDefinition.RawDefinition;
         boolean clamp = definition instanceof TextureDefinition.RawDefinition;
         String mcMetaPath = path + ".mcmeta";
         Path mcMetaResolvedPath = root.resolve(mcMetaPath);
         if (Files.exists(mcMetaResolvedPath)) {
            try {
               JsonObject meta = this.loadMcMeta(mcMetaResolvedPath);
               if (meta.get("texture") != null) {
                  if (meta.get("texture").getAsJsonObject().get("blur") != null) {
                     blur = meta.get("texture").getAsJsonObject().get("blur").getAsBoolean();
                  }

                  if (meta.get("texture").getAsJsonObject().get("clamp") != null) {
                     clamp = meta.get("texture").getAsJsonObject().get("clamp").getAsBoolean();
                  }
               }
            } catch (IOException e) {
               Iris.logger.error("Unable to read the custom texture mcmeta at " + mcMetaPath + ", ignoring: " + e);
            }
         }

         byte[] content = Files.readAllBytes(root.resolve(path));
         if (definition instanceof TextureDefinition.PNGDefinition) {
            customTextureData = new CustomTextureData.PngData(new TextureFilteringData(blur, clamp), content);
         } else if (definition instanceof TextureDefinition.RawDefinition rawDefinition) {
            customTextureData = switch (rawDefinition.getTarget()) {
               case TEXTURE_1D -> new CustomTextureData.RawData1D(
                  content,
                  new TextureFilteringData(blur, clamp),
                  rawDefinition.getInternalFormat(),
                  rawDefinition.getFormat(),
                  rawDefinition.getPixelType(),
                  rawDefinition.getSizeX()
               );
               case TEXTURE_2D -> new CustomTextureData.RawData2D(
                  content,
                  new TextureFilteringData(blur, clamp),
                  rawDefinition.getInternalFormat(),
                  rawDefinition.getFormat(),
                  rawDefinition.getPixelType(),
                  rawDefinition.getSizeX(),
                  rawDefinition.getSizeY()
               );
               case TEXTURE_3D -> new CustomTextureData.RawData3D(
                  content,
                  new TextureFilteringData(blur, clamp),
                  rawDefinition.getInternalFormat(),
                  rawDefinition.getFormat(),
                  rawDefinition.getPixelType(),
                  rawDefinition.getSizeX(),
                  rawDefinition.getSizeY(),
                  rawDefinition.getSizeZ()
               );
               case TEXTURE_RECTANGLE -> new CustomTextureData.RawDataRect(
                  content,
                  new TextureFilteringData(blur, clamp),
                  rawDefinition.getInternalFormat(),
                  rawDefinition.getFormat(),
                  rawDefinition.getPixelType(),
                  rawDefinition.getSizeX(),
                  rawDefinition.getSizeY()
               );
            };
         } else {
            customTextureData = null;
         }
      }

      return customTextureData;
   }

   private JsonObject loadMcMeta(Path mcMetaPath) throws IOException, JsonParseException {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(mcMetaPath), StandardCharsets.UTF_8))) {
         JsonReader jsonReader = new JsonReader(reader);
         return (JsonObject)GSON.getAdapter(JsonObject.class).read(jsonReader);
      }
   }

   public ProgramSet getProgramSet(NamespacedId dimension) {
      ProgramSetInterface overrides = this.overrides.computeIfAbsent(dimension, dim -> {
         if (this.dimensionMap.containsKey(dimension)) {
            String name = this.dimensionMap.get(dimension);
            if (this.dimensionIds.contains(name)) {
               return new ProgramSet(AbsolutePackPath.fromAbsolutePath("/" + name), this.sourceProvider, this.shaderProperties, this);
            }

            Iris.logger.error("Attempted to load dimension folder " + name + " for dimension " + dimension + ", but it does not exist!");
            return ProgramSetInterface.Empty.INSTANCE;
         } else {
            return ProgramSetInterface.Empty.INSTANCE;
         }
      });
      return overrides instanceof ProgramSet ? (ProgramSet)overrides : this.base;
   }

   public IdMap getIdMap() {
      return this.idMap;
   }

   public EnumMap<TextureStage, Object2ObjectMap<String, CustomTextureData>> getCustomTextureDataMap() {
      return this.customTextureDataMap;
   }

   public List<ImageInformation> getIrisCustomImages() {
      return this.irisCustomImages;
   }

   public Object2ObjectMap<String, CustomTextureData> getIrisCustomTextureDataMap() {
      return this.irisCustomTextureDataMap;
   }

   public CustomTextureData getCustomNoiseTexture() {
      return this.customNoiseTexture;
   }

   public LanguageMap getLanguageMap() {
      return this.languageMap;
   }

   public ShaderPackOptions getShaderPackOptions() {
      return this.shaderPackOptions;
   }

   public OptionMenuContainer getMenuContainer() {
      return this.menuContainer;
   }

   public boolean hasFeature(FeatureFlags feature) {
      return this.activeFeatures.contains(feature);
   }

   public Int2ObjectArrayMap<BuiltShaderStorageInfo> getBufferObjects() {
      return this.bufferObjects;
   }

   public Map<NamespacedId, String> getDimensionMap() {
      return this.dimensionMap;
   }
}
