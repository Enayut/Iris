package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.caffeinemc.mods.sodium.client.util.GameRendererStorage;
import net.irisshaders.iris.helpers.MatrixUtils;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.IrisTimeUniforms;
import net.irisshaders.iris.vulkan.IrisVulkanBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vulkan-only counterpart of the state capture in MixinLevelRenderer#iris$setupPipeline: camera matrices (with
 * vanilla's reverse-Z projection converted back to the OpenGL convention packs expect) and the tick delta.
 */
@Mixin(LevelRenderer.class)
public class VKOnly_LevelRendererCapture {
   @Inject(method = "render", at = @At("HEAD"))
   private void iris$vkCaptureState(
      GraphicsResourceAllocator resourceAllocator,
      boolean renderOutline,
      CameraRenderState cameraState,
      GpuBufferSlice terrainFog,
      Vector4f fogColor,
      boolean shouldRenderSky,
      boolean consistentDepthRequired,
      CallbackInfo ci
   ) {
      if (!IrisVulkanBackend.EXPERIMENTAL_RENDERING) {
         return;
      }

      IrisTimeUniforms.updateTime();
      CapturedRenderingState.INSTANCE.setGbufferModelView(cameraState.viewRotationMatrix);
      CapturedRenderingState.INSTANCE
         .setGbufferProjection(MatrixUtils.undoRevZ(new Matrix4f(((GameRendererStorage)Minecraft.getInstance().gameRenderer).sodium$getProjectionMatrix())));
      CapturedRenderingState.INSTANCE.setTickDelta(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
   }
}
