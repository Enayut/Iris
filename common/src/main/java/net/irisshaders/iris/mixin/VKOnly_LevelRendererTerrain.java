package net.irisshaders.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.irisshaders.iris.vulkan.IrisVulkanRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vulkan: the pack's gbuffers_terrain programs draw the opaque terrain into the pack's targets just before vanilla
 * opens its main pass (RenderPearl allows one open pass, and a pipeline must match the pass's attachments), and the
 * main pass then skips the opaque terrain it would have drawn. With classic transparency it also skips the
 * translucent terrain, which the pack's gbuffers_water draws after deferred. Entities stay vanilla's.
 */
@Mixin(LevelRenderer.class)
public class VKOnly_LevelRendererTerrain {
   @Inject(
      method = "lambda$addMainPass$0",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"
      )
   )
   private void iris$vkGbufferTerrain(
      CallbackInfo ci, @Local(argsOnly = true) ChunkSectionsToRender chunkSectionsToRender, @Local RenderTarget mainTarget
   ) {
      IrisVulkanRenderer.beforeMainPass(mainTarget, chunkSectionsToRender);
   }

   @WrapOperation(
      method = "executeSolid",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V"
      )
   )
   private void iris$vkSkipDrawnTerrain(
      ChunkSectionsToRender sections, ChunkSectionLayerGroup group, RenderPass renderPass, GpuSampler sampler, GpuTextureView atlas, boolean wireframe,
      Operation<Void> original
   ) {
      if (!IrisVulkanRenderer.drewOpaqueTerrain()) {
         original.call(sections, group, renderPass, sampler, atlas, wireframe);
      }
   }

   @WrapOperation(
      method = "executeClassicTransparency",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V"
      )
   )
   private void iris$vkSkipDeferredTranslucentTerrain(
      ChunkSectionsToRender sections, ChunkSectionLayerGroup group, RenderPass renderPass, GpuSampler sampler, GpuTextureView atlas, boolean wireframe,
      Operation<Void> original
   ) {
      if (!IrisVulkanRenderer.defersTranslucentTerrain()) {
         original.call(sections, group, renderPass, sampler, atlas, wireframe);
      }
   }
}
