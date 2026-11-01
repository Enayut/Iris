package net.irisshaders.iris.pbr.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

public class PBRSpriteHolder {
   protected TextureAtlasSprite normalSprite;
   protected TextureAtlasSprite specularSprite;

   @Nullable
   public TextureAtlasSprite getNormalSprite() {
      return this.normalSprite;
   }

   public void setNormalSprite(TextureAtlasSprite sprite) {
      this.normalSprite = sprite;
   }

   @Nullable
   public TextureAtlasSprite getSpecularSprite() {
      return this.specularSprite;
   }

   public void setSpecularSprite(TextureAtlasSprite sprite) {
      this.specularSprite = sprite;
   }

   public void close() {
      if (this.normalSprite != null) {
         this.normalSprite.contents().close();
      }

      if (this.specularSprite != null) {
         this.specularSprite.contents().close();
      }
   }
}
