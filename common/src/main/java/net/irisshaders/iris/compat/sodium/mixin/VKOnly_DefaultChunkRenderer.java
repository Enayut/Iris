package net.irisshaders.iris.compat.sodium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.irisshaders.iris.vulkan.IrisVulkanTerrain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vulkan: while Iris draws a terrain layer into the pack's gbuffer targets, Sodium's draw loop uses the pack's
 * program instead of its own. Index buffers, push constants and batches stay Sodium's.
 */
@Mixin(DefaultChunkRenderer.class)
public class VKOnly_DefaultChunkRenderer {
   @WrapOperation(
      method = "render",
      at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setPipeline(Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V")
   )
   private void iris$vkTerrainPipeline(
      RenderPass pass, CompiledRenderPipeline pipeline, Operation<Void> original, @Local(argsOnly = true) TerrainRenderPass terrainPass
   ) {
      if (!IrisVulkanTerrain.setPipeline(pass, terrainPass)) {
         original.call(pass, pipeline);
      }
   }
}
