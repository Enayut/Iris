package net.irisshaders.iris.vertices.sodium.terrain;

public interface VertexEncoderInterface {
   void beginBlock(int var1, byte var2, byte var3, int var4, int var5, int var6);

   void overrideBlock(int var1);

   void restoreBlock();
}
