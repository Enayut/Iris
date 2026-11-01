package net.irisshaders.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline.CreateInfo;
import com.mojang.renderpearl.backend.opengl.GlDevice;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import com.mojang.renderpearl.backend.opengl.VertexArray;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.mixinterface.GlProgramBindings;
import net.irisshaders.iris.mixinterface.GlRenderPipelineAccess;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.IrisProgram;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlRenderPipeline.class)
public class MixinGlRenderPipeline implements GlRenderPipelineAccess {
   @Shadow
   @Final
   private GlProgram program;
   @Unique
   private CreateInfo createInfo;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void iris$setCreateInfo(GlDevice device, CreateInfo createInfo, GlProgram program, VertexArray vertexArray, CallbackInfo ci) {
      this.createInfo = createInfo;
   }

   @Override
   public CreateInfo getCreateInfo() {
      return this.createInfo;
   }

   @WrapOperation(method = "bind", at = @At(value = "FIELD", target = "Lcom/mojang/renderpearl/backend/opengl/GlRenderPipeline;cull:Z"))
   private boolean iris$redirectCull(GlRenderPipeline instance, Operation<Boolean> original) {
      return !ShadowRenderingState.areShadowsCurrentlyBeingRendered() && (Boolean)original.call(new Object[]{instance});
   }

   @WrapOperation(method = "bind", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_depthFunc(I)V"))
   private void iris$pass(int compareOp, Operation<Void> original) {
      WorldRenderingPipeline p = Iris.getPipelineManager().getPipelineNullable();
      if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
         original.call(new Object[]{switch (compareOp) {
            case 512 -> 512;
            case 513 -> 516;
            case 514 -> 514;
            case 515 -> 518;
            case 516 -> 513;
            case 517 -> 517;
            case 518 -> 515;
            case 519 -> 519;
            default -> throw new IllegalStateException("Unexpected value: " + compareOp);
         }});
      } else {
         original.call(new Object[]{compareOp});
      }
   }

   @WrapOperation(method = "bind", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_enableBlend(I)V"))
   private void iris$enableBlend(int index, Operation<Void> original) {
      if (this.program instanceof IrisProgram && this.createInfo.colorTargetStates().size() == 1) {
         IrisRenderSystem.enableBlend();
      } else {
         original.call(new Object[]{index});
      }
   }

   @WrapOperation(method = "bind", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_disableBlend(I)V"))
   private void iris$disableBlend(int index, Operation<Void> original) {
      if (this.program instanceof IrisProgram && this.createInfo.colorTargetStates().size() == 1) {
         IrisRenderSystem.disableBlend();
      } else {
         original.call(new Object[]{index});
      }
   }

   @Inject(method = "bind", at = @At("RETURN"))
   private void iris$bind(CallbackInfo ci) {
      if (this.program instanceof IrisProgram irisProgram) {
         ((GlProgramBindings)this.program).iris$setupBindings(this.createInfo.uniforms(), this.createInfo.pushConstantsSize());
         irisProgram.iris$setupState(this.createInfo.uniforms());
      }
   }
}
