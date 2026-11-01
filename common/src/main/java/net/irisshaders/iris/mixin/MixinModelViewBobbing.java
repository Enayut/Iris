package net.irisshaders.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinModelViewBobbing {
   @Shadow
   @Final
   Minecraft minecraft;
   @Shadow
   @Final
   private Camera mainCamera;
   @Unique
   private Matrix4fc bobbingEffectsModel;
   @Unique
   private boolean areShadersOn;
   @Unique
   private Matrix4fc bobStack;

   @Inject(method = "renderLevel", at = @At("HEAD"))
   private void iris$saveShadersOn(CallbackInfo ci) {
      this.areShadersOn = Iris.isPackInUseQuick();
   }

   @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;"))
   private Matrix4f iris$applyBobbingToModelView(
      Matrix4f instance, Matrix4fc right, Operation<Matrix4f> original, @Local(name = "cameraState") CameraRenderState cameraRenderState
   ) {
      if (!this.areShadersOn) {
         return (Matrix4f)original.call(new Object[]{instance, right});
      }

      this.bobStack = new Matrix4f(right);
      return instance;
   }

   @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;rotate(FLorg/joml/Vector3fc;)Lorg/joml/Matrix4f;"))
   private Matrix4f iris$applySpinningRotate(Matrix4f instance, float angle, Vector3fc axis, Operation<Matrix4f> original) {
      if (!this.areShadersOn) {
         return (Matrix4f)original.call(new Object[]{instance, angle, axis});
      }

      ((Matrix4f)this.bobStack).rotate(angle, axis);
      return instance;
   }

   @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;scale(FFF)Lorg/joml/Matrix4f;"))
   private Matrix4f iris$applySpinningScale(Matrix4f instance, float x, float y, float z, Operation<Matrix4f> original) {
      if (!this.areShadersOn) {
         return (Matrix4f)original.call(new Object[]{instance, x, y, z});
      }

      ((Matrix4f)this.bobStack).scale(x, y, z);
      return instance;
   }

   @WrapOperation(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V"
      )
   )
   private void iris$renderLevel(
      LevelRenderer instance,
      GraphicsResourceAllocator graphicsResourceAllocator,
      boolean y,
      CameraRenderState cameraRenderState,
      GpuBufferSlice gpuBufferSlice,
      Vector4f vector4f,
      boolean b,
      boolean x,
      Operation<Void> original
   ) {
      if (this.areShadersOn) {
         cameraRenderState.viewRotationMatrix.mulLocal(this.bobStack);
      }

      original.call(new Object[]{instance, graphicsResourceAllocator, y, cameraRenderState, gpuBufferSlice, vector4f, b, x});
   }
}
