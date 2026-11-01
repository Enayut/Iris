package net.irisshaders.iris.mixin.texture;

import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
   @Accessor("texturesByName")
   Map<Identifier, TextureAtlasSprite> getTexturesByName();

   @Accessor("maxMipLevel")
   int getMaxLevel();

   @Invoker("getWidth")
   int callGetWidth();

   @Invoker("getHeight")
   int callGetHeight();
}
