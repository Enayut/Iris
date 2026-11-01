package net.irisshaders.iris.mixin.vertices.immediate;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer.Draw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StagedVertexBuffer.class)
public class MixinBufferSource {
   @WrapMethod(method = "getVertexBuilder")
   private VertexConsumer iris$redirectBegin(Draw draw, Operation<VertexConsumer> original) {
      ImmediateState.skipExtension.set(this.iris$notRenderingLevel());
      VertexConsumer builder = (VertexConsumer)original.call(new Object[]{draw});
      ImmediateState.skipExtension.set(false);
      return builder;
   }

   @Inject(method = "upload", at = @At("HEAD"))
   private void iris$beforeFlushBuffer(CallbackInfo ci) {
      if (this.iris$notRenderingLevel()) {
         ImmediateState.renderWithExtendedVertexFormat = false;
      }
   }

   @Inject(method = "upload", at = @At("RETURN"))
   private void iris$afterFlushBuffer(CallbackInfo ci) {
      if (this.iris$notRenderingLevel()) {
         ImmediateState.renderWithExtendedVertexFormat = true;
      }
   }

   @Unique
   private boolean iris$notRenderingLevel() {
      return !ImmediateState.isRenderingLevel;
   }
}
