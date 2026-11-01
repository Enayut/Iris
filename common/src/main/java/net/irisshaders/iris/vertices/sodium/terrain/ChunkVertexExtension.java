package net.irisshaders.iris.vertices.sodium.terrain;

public interface ChunkVertexExtension {
   void iris$setData(byte var1, byte var2, int var3, int var4, int var5, int var6);

   void iris$ignoresMidBlock(boolean var1);

   void iris$copyData(ChunkVertexExtension var1);

   int getLocalPosX();

   int getLocalPosY();

   int getLocalPosZ();

   int getBlockId();

   byte getRenderType();

   byte getBlockEmission();

   boolean ignoreMidBlock();
}
