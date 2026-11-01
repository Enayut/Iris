package net.irisshaders.iris.mixin.texture;

import java.util.List;
import net.minecraft.client.renderer.texture.SpriteContents.AnimatedTexture;
import net.minecraft.client.renderer.texture.SpriteContents.FrameInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AnimatedTexture.class)
public interface SpriteContentsAnimatedTextureAccessor {
   @Accessor("frames")
   List<FrameInfo> getFrames();
}
