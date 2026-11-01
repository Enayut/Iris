package net.irisshaders.iris.mixin;

import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.backend.opengl.GlConst;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.vertices.ImmediateState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlConst.class)
public class UndoReverseZThree {
   @Inject(method = "toGl(Lcom/mojang/renderpearl/api/pipeline/CompareOp;)I", at = @At("HEAD"), cancellable = true)
   private static void iris$to(CompareOp compareOp, CallbackInfoReturnable<Integer> cir) {
      if (Iris.isPackInUseQuick() && ImmediateState.isRenderingLevel) {
         cir.setReturnValue(switch (compareOp) {
            case ALWAYS_PASS -> 519;
            case LESS_THAN -> 516;
            case LESS_THAN_OR_EQUAL -> 518;
            case EQUAL -> 514;
            case NOT_EQUAL -> 517;
            case GREATER_THAN_OR_EQUAL -> 515;
            case GREATER_THAN -> 513;
            case NEVER_PASS -> 512;
            default -> throw new MatchException(null, null);
         });
      }
   }
}
