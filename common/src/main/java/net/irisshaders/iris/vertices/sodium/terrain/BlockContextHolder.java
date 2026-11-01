package net.irisshaders.iris.vertices.sodium.terrain;

public class BlockContextHolder {
   private byte blockEmission;
   private int blockId;
   private byte renderType;
   private int localPosX;
   private int localPosY;
   private int localPosZ;
   private boolean ignoreMidBlock;
   private int oldId = -1;

   public int getBlockId() {
      return this.blockId;
   }

   public byte getRenderType() {
      return this.renderType;
   }

   public byte getBlockEmission() {
      return this.blockEmission;
   }

   public int getLocalPosX() {
      return this.localPosX;
   }

   public int getLocalPosY() {
      return this.localPosY;
   }

   public int getLocalPosZ() {
      return this.localPosZ;
   }

   public void setBlockData(int blockId, byte renderType, byte blockEmission, int localPosX, int localPosY, int localPosZ) {
      this.blockId = blockId;
      this.renderType = renderType;
      this.blockEmission = blockEmission;
      this.localPosX = localPosX;
      this.localPosY = localPosY;
      this.localPosZ = localPosZ;
   }

   public boolean ignoreMidBlock() {
      return this.ignoreMidBlock;
   }

   public void setIgnoreMidBlock(boolean ignoreMidBlock) {
      this.ignoreMidBlock = ignoreMidBlock;
   }

   public void overrideBlock(int block) {
      if (this.blockId != block) {
         if (this.oldId == -1) {
            this.oldId = this.blockId;
         }

         this.blockId = block;
      }
   }

   public void restoreBlock() {
      if (this.oldId != -1) {
         this.blockId = this.oldId;
         this.oldId = -1;
      }
   }
}
