package net.irisshaders.iris.vertices;

public final class ExtendedDataHelper {
   public static final short BLOCK_RENDER_TYPE = -1;
   public static final short FLUID_RENDER_TYPE = 1;

   public static int packMidBlock(float x, float y, float z) {
      return (int)(x * 64.0F) & 0xFF | ((int)(y * 64.0F) & 0xFF) << 8 | ((int)(z * 64.0F) & 0xFF) << 16;
   }

   public static int computeMidBlock(float x, float y, float z, int localPosX, int localPosY, int localPosZ) {
      return packMidBlock((localPosX & 65535) + 0.5F - x, (localPosY & 65535) + 0.5F - y, (localPosZ & 65535) + 0.5F - z);
   }
}
