package net.irisshaders.iris.compat.sodium.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder.Vertex;
import net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Vertex.class)
public class MixinChunkVertex implements ChunkVertexExtension {
   private byte blockEmission;
   private int blockId;
   private byte renderType;
   private int localPosX;
   private int localPosY;
   private int localPosZ;
   private boolean ignoresMidBlock = false;

   @Override
   public void iris$setData(byte blockEmission, byte renderType, int blockId, int localX, int localY, int localZ) {
      this.blockEmission = blockEmission;
      this.renderType = renderType;
      this.blockId = blockId;
      this.localPosX = localX;
      this.localPosY = localY;
      this.localPosZ = localZ;
   }

   @Override
   public void iris$ignoresMidBlock(boolean setIgnore) {
      this.ignoresMidBlock = setIgnore;
   }

   @Override
   public void iris$copyData(ChunkVertexExtension dest) {
      dest.iris$setData(this.blockEmission, this.renderType, this.blockId, this.localPosX, this.localPosY, this.localPosZ);
   }

   @Inject(method = "copyVertexTo", at = @At("HEAD"))
   private static void iris$copyVertex(Vertex from, Vertex _to, CallbackInfo ci) {
      ((ChunkVertexExtension)from).iris$copyData((ChunkVertexExtension)_to);
   }

   @Override
   public int getLocalPosX() {
      return this.localPosX;
   }

   @Override
   public int getLocalPosY() {
      return this.localPosY;
   }

   @Override
   public int getLocalPosZ() {
      return this.localPosZ;
   }

   @Override
   public int getBlockId() {
      return this.blockId;
   }

   @Override
   public byte getRenderType() {
      return this.renderType;
   }

   @Override
   public byte getBlockEmission() {
      return this.blockEmission;
   }

   @Override
   public boolean ignoreMidBlock() {
      return this.ignoresMidBlock;
   }
}
