package net.irisshaders.iris.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Set;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

public class VanillaParameters extends GeometryInfoParameters {
   public final AlphaTest alpha;
   public final ShaderAttributeInputs inputs;
   public final boolean hasChunkOffset;
   private final boolean isLines;
   private final boolean isClouds;

   public VanillaParameters(
      Patch patch,
      Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
      Set<String> textureOverrides,
      AlphaTest alpha,
      boolean isLines,
      boolean isClouds,
      boolean hasChunkOffset,
      ShaderAttributeInputs inputs,
      boolean hasGeometry,
      boolean hasTesselation
   ) {
      super(patch, textureMap, textureOverrides, hasGeometry, hasTesselation);
      this.alpha = alpha;
      this.isLines = isLines;
      this.isClouds = isClouds;
      this.hasChunkOffset = hasChunkOffset;
      this.inputs = inputs;
   }

   public boolean isLines() {
      return this.isLines;
   }

   public boolean isClouds() {
      return this.isClouds;
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
      result = 31 * result + (this.inputs == null ? 0 : this.inputs.hashCode());
      result = 31 * result + (this.hasChunkOffset ? 1231 : 1237);
      result = 31 * result + (this.isLines ? 1231 : 1237);
      return 31 * result + (this.isClouds ? 1231 : 1237);
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

      VanillaParameters other = (VanillaParameters)obj;
      if (this.alpha == null) {
         if (other.alpha != null) {
            return false;
         }
      } else if (!this.alpha.equals(other.alpha)) {
         return false;
      }

      if (this.inputs == null) {
         if (other.inputs != null) {
            return false;
         }
      } else if (!this.inputs.equals(other.inputs)) {
         return false;
      }

      if (this.hasChunkOffset != other.hasChunkOffset) {
         return false;
      } else {
         return this.isClouds != other.isClouds ? false : this.isLines == other.isLines;
      }
   }
}
