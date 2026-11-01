package net.irisshaders.iris.mixin.texture;

import net.minecraft.client.renderer.texture.SpriteContents.AnimatedTexture;
import net.minecraft.client.renderer.texture.SpriteContents.AnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AnimationState.class)
public interface SpriteContentsTickerAccessor {
   @Accessor("frame")
   int getFrame();

   @Accessor("frame")
   void setFrame(int var1);

   @Accessor("subFrame")
   int getSubFrame();

   @Accessor("subFrame")
   void setSubFrame(int var1);

   @Accessor("animationInfo")
   AnimatedTexture getAnimationInfo();
}
