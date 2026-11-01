package net.irisshaders.iris.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Set;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

public class SodiumParameters extends Parameters {
   public final AlphaTest alpha;
   public final boolean shadow;

   public SodiumParameters(
      Patch patch, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap, Set<String> textureOverrides, AlphaTest alpha, boolean shadow
   ) {
      super(patch, textureMap, textureOverrides);
      this.alpha = alpha;
      this.shadow = shadow;
   }

   @Override
   public AlphaTest getAlphaTest() {
      return this.alpha;
   }

   @Override
   public TextureStage getTextureStage() {
      return TextureStage.GBUFFERS_AND_SHADOW;
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = super.hashCode();
      result = 31 * result + (this.alpha == null ? 0 : this.alpha.hashCode());
      return 31 * result + (this.shadow ? 1231 : 1237);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }

      if (!super.equals(obj)) {
         return false;
      }

      if (this.getClass() != obj.getClass()) {
         return false;
      }

      SodiumParameters other = (SodiumParameters)obj;
      if (this.alpha == null) {
         if (other.alpha != null) {
            return false;
         }
      } else if (!this.alpha.equals(other.alpha)) {
         return false;
      }

      return this.shadow == other.shadow;
   }
}
