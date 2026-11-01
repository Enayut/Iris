package net.irisshaders.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor.Attachment;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor.Builder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import com.mojang.renderpearl.backend.opengl.GlCommandEncoder;
import java.util.Optional;
import java.util.OptionalDouble;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.vertices.ImmediateState;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GlCommandEncoder.class)
public class UndoReverseZFive {
   @WrapMethod(
      method = "clearColorAndDepthTextures(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V"
   )
   private void iris$change(GpuTexture colorTexture, Vector4fc clearColor, GpuTexture depthTexture, double clearDepth, Operation<Void> original) {
      original.call(
         new Object[]{
            colorTexture, clearColor, depthTexture, this.saturate(Iris.isPackInUseQuick() && ImmediateState.isRenderingLevel ? 1.0 - clearDepth : clearDepth)
         }
      );
   }

   @WrapMethod(
      method = "clearColorAndDepthTextures(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/renderpearl/api/textures/GpuTexture;DIIIII)V"
   )
   private void iris$change3(
      GpuTexture colorTexture,
      Vector4fc clearColor,
      GpuTexture depthTexture,
      double clearDepth,
      int regionX,
      int regionY,
      int regionWidth,
      int regionHeight,
      int mipLevel,
      Operation<Void> original
   ) {
      original.call(
         new Object[]{
            colorTexture,
            clearColor,
            depthTexture,
            this.saturate(Iris.isPackInUseQuick() && ImmediateState.isRenderingLevel ? 1.0 - clearDepth : clearDepth),
            regionX,
            regionY,
            regionWidth,
            regionHeight,
            mipLevel
         }
      );
   }

   @WrapMethod(method = "clearDepthTexture")
   private void iris$change2(GpuTexture depthTexture, double clearDepth, Operation<Void> original) {
      original.call(new Object[]{depthTexture, this.saturate(Iris.isPackInUseQuick() && ImmediateState.isRenderingLevel ? 1.0 - clearDepth : clearDepth)});
   }

   @WrapMethod(method = "createRenderPass")
   private RenderPassBackend iris$change4(RenderPassDescriptor descriptor, Operation<RenderPassBackend> original) {
      if (Iris.isPackInUseQuick() && ImmediateState.isRenderingLevel) {
         if (descriptor.depthAttachment() != null && ((OptionalDouble)descriptor.depthAttachment().clearValue()).isPresent()) {
            Builder b = RenderPassDescriptor.builder(descriptor.label());

            for (int i = 0; i < descriptor.colorAttachments().size(); i++) {
               Attachment<Optional<Vector4fc>> x = (Attachment<Optional<Vector4fc>>)descriptor.colorAttachments().get(i);
               if (x == null) {
                  b.withUnusedColorAttachment();
               } else {
                  b.withColorAttachment(x.textureView(), (Optional)x.clearValue());
               }
            }

            b.withDepthAttachment(
               descriptor.depthAttachment().textureView(),
               ((OptionalDouble)descriptor.depthAttachment().clearValue()).isPresent()
                  ? OptionalDouble.of(this.saturate(1.0 - ((OptionalDouble)descriptor.depthAttachment().clearValue()).getAsDouble()))
                  : OptionalDouble.empty()
            );
            b.withRenderArea(descriptor.renderArea());
            return (RenderPassBackend)original.call(new Object[]{b.build()});
         } else {
            return (RenderPassBackend)original.call(new Object[]{descriptor});
         }
      } else {
         return (RenderPassBackend)original.call(new Object[]{descriptor});
      }
   }

   @Unique
   private double saturate(double v) {
      return Math.clamp(v, 0.0, 1.0);
   }
}
