package net.irisshaders.iris.mixin.vertices.block_rendering;

import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CardinalLighting.class)
public class MixinClientLevel {
   @ModifyVariable(method = "byFace", at = @At("HEAD"), argsOnly = true)
   private Direction iris$maybeDisableDirectionalShading(Direction value) {
      return WorldRenderingSettings.INSTANCE.shouldDisableDirectionalShading() ? Direction.UP : value;
   }
}
