package net.irisshaders.iris.pbr.texture;

import org.jetbrains.annotations.Nullable;

public class PBRAtlasHolder {
   protected PBRAtlasTexture normalAtlas;
   protected PBRAtlasTexture specularAtlas;

   @Nullable
   public PBRAtlasTexture getNormalAtlas() {
      return this.normalAtlas;
   }

   public void setNormalAtlas(PBRAtlasTexture atlas) {
      this.normalAtlas = atlas;
   }

   @Nullable
   public PBRAtlasTexture getSpecularAtlas() {
      return this.specularAtlas;
   }

   public void setSpecularAtlas(PBRAtlasTexture atlas) {
      this.specularAtlas = atlas;
   }

   public void cycleAnimationFrames() {
      if (this.normalAtlas != null) {
         this.normalAtlas.cycleAnimationFrames();
      }

      if (this.specularAtlas != null) {
         this.specularAtlas.cycleAnimationFrames();
      }
   }
}
