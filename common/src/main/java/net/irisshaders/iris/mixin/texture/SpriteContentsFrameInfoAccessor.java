package net.irisshaders.iris.mixin.texture;

import net.minecraft.client.renderer.texture.SpriteContents.FrameInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FrameInfo.class)
public interface SpriteContentsFrameInfoAccessor {
   @Accessor("index")
   int getIndex();

   @Accessor("time")
   int getTime();
}
