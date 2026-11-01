package net.irisshaders.iris.mixin;

import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockStateBase.class, priority = 990)
public abstract class MixinBlockStateBehavior {
   @Shadow
   public abstract Block getBlock();

   @Shadow
   protected abstract BlockState asState();

   @Inject(method = "getShadeBrightness", at = @At("RETURN"), cancellable = true)
   public void getShadeBrightness(BlockGetter pBlockBehaviour$BlockStateBase0, BlockPos pBlockPos1, CallbackInfoReturnable<Float> cir) {
      float originalValue = (Float)cir.getReturnValue();
      float aoLightValue = WorldRenderingSettings.INSTANCE.getAmbientOcclusionLevel();
      cir.setReturnValue(1.0F - aoLightValue * (1.0F - originalValue));
   }
}
