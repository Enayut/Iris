package net.irisshaders.iris.pbr.texture;

import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.Nullable;

public enum PBRType {
   NORMAL("_n", 2139095039),
   SPECULAR("_s", 0);

   private static final PBRType[] VALUES = values();
   private final String suffix;
   private final int defaultValue;

   PBRType(String suffix, int defaultValue) {
      this.suffix = suffix;
      this.defaultValue = defaultValue;
   }

   @Nullable
   public static String removeSuffix(String path) {
      int extensionIndex = FilenameUtils.indexOfExtension(path);
      String pathNoExtension = path.substring(0, extensionIndex);
      PBRType type = fromFileLocation(pathNoExtension);
      if (type != null) {
         String suffix = type.getSuffix();
         String basePathNoExtension = pathNoExtension.substring(0, pathNoExtension.length() - suffix.length());
         return basePathNoExtension + path.substring(extensionIndex);
      } else {
         return null;
      }
   }

   @Nullable
   public static PBRType fromFileLocation(String location) {
      for (PBRType type : VALUES) {
         if (location.endsWith(type.getSuffix())) {
            return type;
         }
      }

      return null;
   }

   public String getSuffix() {
      return this.suffix;
   }

   public int getDefaultValue() {
      return this.defaultValue;
   }

   public String appendSuffix(String path) {
      int extensionIndex = FilenameUtils.indexOfExtension(path);
      return extensionIndex != -1 ? path.substring(0, extensionIndex) + this.suffix + path.substring(extensionIndex) : path + this.suffix;
   }
}
