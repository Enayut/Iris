package net.irisshaders.iris.pbr.format;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class TextureFormatRegistry {
   public static final TextureFormatRegistry INSTANCE = new TextureFormatRegistry();
   private final Map<String, TextureFormat.Factory> factoryMap = new HashMap<>();

   public void register(String name, TextureFormat.Factory factory) {
      this.factoryMap.put(name, factory);
   }

   @Nullable
   public TextureFormat.Factory getFactory(String name) {
      return this.factoryMap.get(name);
   }

   static {
      INSTANCE.register("lab-pbr", LabPBRTextureFormat::new);
   }
}
