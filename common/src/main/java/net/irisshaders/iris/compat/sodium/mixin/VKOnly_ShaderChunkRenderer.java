package net.irisshaders.iris.compat.sodium.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vulkan: Sodium caches its terrain pipelines statically, built for the vertex format of the renderer that first
 * asked. Iris switches between Sodium's compact format and its extended one when a pack's terrain programs come and
 * go, so the cache is dropped whenever a renderer with another format asks; otherwise Sodium's own translucent pass
 * would read the extended vertex buffers with the compact stride. (On OpenGL Iris overrides these pipelines instead.)
 */
@Mixin(ShaderChunkRenderer.class)
public class VKOnly_ShaderChunkRenderer {
   @Shadow
   @Final
   private static Map<TerrainRenderPass, RenderPipeline> programs;
   @Shadow
   @Final
   private static Map<TerrainRenderPass, OitPipelineSet> oitPrograms;
   @Shadow
   @Final
   protected VertexFormat vertexFormat;
   @Unique
   private static VertexFormat iris$cachedFormat;

   @Inject(method = "compileProgram", at = @At("HEAD"))
   private void iris$dropPipelinesForOtherFormat(TerrainRenderPass pass, OitStage stage, CallbackInfoReturnable<RenderPipeline> cir) {
      if (iris$cachedFormat != this.vertexFormat) {
         programs.clear();
         oitPrograms.clear();
         iris$cachedFormat = this.vertexFormat;
      }
   }
}
