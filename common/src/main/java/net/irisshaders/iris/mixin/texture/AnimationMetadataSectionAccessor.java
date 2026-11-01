package net.irisshaders.iris.mixin.texture;

import java.util.Optional;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AnimationMetadataSection.class)
public interface AnimationMetadataSectionAccessor {
   @Accessor("frameWidth")
   Optional<Integer> getFrameWidth();

   @Mutable
   @Accessor("frameWidth")
   void setFrameWidth(Optional<Integer> var1);

   @Accessor("frameHeight")
   Optional<Integer> getFrameHeight();

   @Mutable
   @Accessor("frameHeight")
   void setFrameHeight(Optional<Integer> var1);
}
