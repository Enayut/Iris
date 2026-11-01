package net.irisshaders.iris.pbr.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.irisshaders.iris.pbr.mipmap.CustomMipmapGenerator;
import net.irisshaders.iris.pbr.texture.PBRType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.Nullable;

public interface TextureFormat {
   String name();

   @Nullable
   String version();

   default List<String> getDefines() {
      List<String> defines = new ArrayList<>();
      String defineName = this.name().toUpperCase(Locale.ROOT).replaceAll("-", "_");
      String define = "MC_TEXTURE_FORMAT_" + defineName;
      defines.add(define);
      String version = this.version();
      if (version != null) {
         String defineVersion = version.replaceAll("[.-]", "_");
         String versionDefine = define + "_" + defineVersion;
         defines.add(versionDefine);
      }

      return defines;
   }

   boolean canInterpolateValues(PBRType var1);

   default void setupTextureParameters(PBRType pbrType, AbstractTexture texture) {
      if (!this.canInterpolateValues(pbrType)) {
         texture.getTexture().iris$markMipmapNonLinear();
      }
   }

   @Nullable
   CustomMipmapGenerator getMipmapGenerator(PBRType var1);

   interface Factory {
      TextureFormat createFormat(String var1, @Nullable String var2);
   }
}
