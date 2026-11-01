package net.irisshaders.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.irisshaders.iris.layer.RenderingWrapper;
import net.irisshaders.iris.layer.WrappedPreparedRenderType;
import net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PreparedRenderType.class)
public class MixinPreparedRenderType implements WrappedPreparedRenderType {
   @Unique
   private RenderingWrapper wrapper;

   @WrapMethod(method = "draw")
   private void iris$wrapBuffer(ExecuteInfo info, RenderPass renderPass, RenderPipeline renderPipeline, Operation<Void> original) {
      if (this.wrapper != null) {
         this.wrapper.setup();
      }

      original.call(new Object[]{info, renderPass, renderPipeline});
      if (this.wrapper != null) {
         this.wrapper.clear();
      }
   }

   @Override
   public void setRenderWrapper(RenderingWrapper wrapper) {
      this.wrapper = wrapper;
   }
}
