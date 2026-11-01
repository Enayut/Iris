package net.irisshaders.iris.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import net.minecraft.resources.Identifier;

public class IrisConfig {
   private static final String COMMENT = "This file stores configuration options for Iris, such as the currently active shaderpack";
   private final Path propertiesPath;
   private final Path excludedPath;
   private String shaderPackName;
   private boolean enableShaders;
   private boolean allowUnknownShaders;
   private boolean enableDebugOptions;
   private List<Identifier> shadersToSkip = new ArrayList<>();
   private boolean disableUpdateMessage;
   private static Gson GSON = new Gson();

   public IrisConfig(Path propertiesPath, Path excluded) {
      this.shaderPackName = null;
      this.enableShaders = true;
      this.allowUnknownShaders = false;
      this.enableDebugOptions = false;
      this.disableUpdateMessage = false;
      this.propertiesPath = propertiesPath;
      this.excludedPath = excluded;
   }

   public void initialize() throws IOException {
      this.load();
      if (!Files.exists(this.propertiesPath)) {
         this.save();
      }
   }

   public boolean isInternal() {
      return false;
   }

   public Optional<String> getShaderPackName() {
      return Optional.ofNullable(this.shaderPackName);
   }

   public void setShaderPackName(String name) {
      if (name != null && !name.equals("(internal)") && !name.isEmpty()) {
         this.shaderPackName = name;
      } else {
         this.shaderPackName = null;
      }
   }

   public boolean areShadersEnabled() {
      return this.enableShaders;
   }

   public boolean areDebugOptionsEnabled() {
      return this.enableDebugOptions;
   }

   public boolean shouldDisableUpdateMessage() {
      return this.disableUpdateMessage;
   }

   public void setDebugEnabled(boolean enabled) {
      this.enableDebugOptions = enabled;
   }

   public void setShadersEnabled(boolean enabled) {
      this.enableShaders = enabled;
   }

   public void load() throws IOException {
      if (Files.exists(this.excludedPath)) {
         JsonArray json = JsonParser.parseString(Files.readString(this.excludedPath)).getAsJsonObject().getAsJsonArray("excluded");

         for (int i = 0; i < json.size(); i++) {
            Identifier resource = Identifier.tryParse(json.get(i).getAsString());
            if (resource == null) {
               Iris.logger.warn("Unknown shader " + json.get(i).getAsString());
            }

            this.shadersToSkip.add(resource);
         }
      } else {
         JsonObject defaultV = new JsonObject();
         JsonArray array = new JsonArray();
         array.add("put:valuesHere");
         defaultV.add("excluded", array);
         Files.writeString(this.excludedPath, GSON.toJson(defaultV));
      }

      if (Files.exists(this.propertiesPath)) {
         Properties properties = new Properties();

         try (InputStream is = Files.newInputStream(this.propertiesPath)) {
            properties.load(is);
         }

         this.shaderPackName = properties.getProperty("shaderPack");
         this.enableShaders = !"false".equals(properties.getProperty("enableShaders"));
         this.allowUnknownShaders = "true".equals(properties.getProperty("allowUnknownShaders"));
         this.enableDebugOptions = "true".equals(properties.getProperty("enableDebugOptions"));
         this.disableUpdateMessage = "true".equals(properties.getProperty("disableUpdateMessage"));

         try {
            IrisVideoSettings.shadowDistance = Integer.parseInt(properties.getProperty("maxShadowRenderDistance", "32"));
            IrisVideoSettings.colorSpace = ColorSpace.valueOf(properties.getProperty("colorSpace", "SRGB"));
         } catch (IllegalArgumentException e) {
            Iris.logger.error("Shadow distance setting reset; value is invalid.");
            IrisVideoSettings.shadowDistance = 32;
            IrisVideoSettings.colorSpace = ColorSpace.SRGB;
            this.save();
         }

         if (this.shaderPackName != null && (this.shaderPackName.equals("(internal)") || this.shaderPackName.isEmpty())) {
            this.shaderPackName = null;
         }
      }
   }

   public void save() throws IOException {
      Properties properties = new Properties();
      properties.setProperty("shaderPack", this.getShaderPackName().orElse(""));
      properties.setProperty("enableShaders", this.enableShaders ? "true" : "false");
      properties.setProperty("allowUnknownShaders", this.allowUnknownShaders ? "true" : "false");
      properties.setProperty("enableDebugOptions", this.enableDebugOptions ? "true" : "false");
      properties.setProperty("disableUpdateMessage", this.disableUpdateMessage ? "true" : "false");
      properties.setProperty("maxShadowRenderDistance", String.valueOf(IrisVideoSettings.shadowDistance));
      properties.setProperty("colorSpace", IrisVideoSettings.colorSpace.name());

      try (OutputStream os = Files.newOutputStream(this.propertiesPath)) {
         properties.store(os, "This file stores configuration options for Iris, such as the currently active shaderpack");
      }
   }

   public boolean shouldAllowUnknownShaders() {
      return this.allowUnknownShaders;
   }

   public boolean shouldSkip(Identifier value) {
      return this.shadersToSkip.contains(value);
   }

   public void setUnknown(boolean b) throws IOException {
      this.allowUnknownShaders = b;
      this.save();
   }
}
