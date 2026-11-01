package net.irisshaders.iris.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Set;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

public abstract class GeometryInfoParameters extends Parameters {
   public final boolean hasGeometry;
   public final boolean hasTesselation;

   public GeometryInfoParameters(
      Patch patch,
      Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
      Set<String> textureOverrides,
      boolean hasGeometry,
      boolean hasTesselation
   ) {
      super(patch, textureMap, textureOverrides);
      this.hasGeometry = hasGeometry;
      this.hasTesselation = hasTesselation;
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = super.hashCode();
      return 31 * result + (this.hasGeometry ? 1231 : 1237);
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

      GeometryInfoParameters other = (GeometryInfoParameters)obj;
      return this.hasGeometry == other.hasGeometry;
   }
}
