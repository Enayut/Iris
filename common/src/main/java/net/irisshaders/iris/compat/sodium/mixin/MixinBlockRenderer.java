package net.irisshaders.iris.compat.sodium.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder.Vertex;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.irisshaders.iris.shaderpack.materialmap.BlockRenderType;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension;
import net.irisshaders.iris.vertices.sodium.terrain.VertexEncoderInterface;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderer.class)
public class MixinBlockRenderer implements VertexEncoderInterface {
   @Unique
   private int blockId;
   @Unique
   private byte isFluid;
   @Unique
   private byte lightEmission;
   @Unique
   private int localX;
   @Unique
   private int localY;
   @Unique
   private int localZ;
   @Unique
   private int lastBlockId;
   @Unique
   private ChunkSectionLayer overrideRenderType;

   @Override
   public void beginBlock(int blockId, byte isFluid, byte lightEmission, int x, int y, int z) {
      this.blockId = blockId;
      this.isFluid = isFluid;
      this.lightEmission = lightEmission;
      this.localX = x;
      this.localY = y;
      this.localZ = z;
   }

   @Inject(
      method = "renderModel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/caffeinemc/mods/sodium/client/services/PlatformModelEmitter;emitModel(Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;Ljava/util/function/Predicate;Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;Lnet/minecraft/util/RandomSource;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/caffeinemc/mods/sodium/client/services/PlatformModelEmitter$Bufferer;)V"
      )
   )
   private void handleShaderPackTransparency(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
      if (!(((Object) this) instanceof BlockRenderer) || WorldRenderingSettings.INSTANCE.getBlockTypeIds() == null) {
         this.overrideRenderType = null;
      } else if (state == null) {
         this.overrideRenderType = null;
      } else {
         BlockRenderType blockRenderType = WorldRenderingSettings.INSTANCE.getBlockTypeIds().get(state.getBlock());
         if (blockRenderType == null) {
            this.overrideRenderType = null;
         } else {
            ChunkSectionLayer layer = switch (blockRenderType) {
               case SOLID -> ChunkSectionLayer.SOLID;
               case CUTOUT, CUTOUT_MIPPED -> ChunkSectionLayer.CUTOUT;
               case TRANSLUCENT -> ChunkSectionLayer.TRANSLUCENT;
            };
            this.overrideRenderType = layer;
         }
      }
   }

   @Inject(method = "processQuad", at = @At("HEAD"))
   private void iris$overrideQuad(MutableQuadViewImpl quad, CallbackInfo ci) {
      if (this.overrideRenderType != null) {
         quad.setRenderType(this.overrideRenderType);
      }
   }

   @Override
   public void overrideBlock(int anInt) {
      if (this.lastBlockId != -1) {
         this.lastBlockId = this.blockId;
      }

      this.blockId = anInt;
   }

   @Override
   public void restoreBlock() {
      if (this.lastBlockId != -1) {
         this.blockId = this.lastBlockId;
         this.lastBlockId = -1;
      }
   }

   @Inject(
      method = "bufferQuad",
      at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;x:F")
   )
   private void iris$writeVertex(MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci, @Local Vertex vertex) {
      ((ChunkVertexExtension)vertex).iris$setData(this.lightEmission, this.isFluid, this.blockId, this.localX, this.localY, this.localZ);
   }
}
