package net.irisshaders.iris.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Set;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

public class TextureStageParameters extends Parameters {
   private final TextureStage stage;

   public TextureStageParameters(
      Patch patch, TextureStage stage, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap, Set<String> textureOverrides
   ) {
      super(patch, textureMap, textureOverrides);
      this.stage = stage;
   }

   @Override
   public TextureStage getTextureStage() {
      return this.stage;
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = super.hashCode();
      return 31 * result + (this.stage == null ? 0 : this.stage.hashCode());
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

      TextureStageParameters other = (TextureStageParameters)obj;
      return this.stage == other.stage;
   }
}
