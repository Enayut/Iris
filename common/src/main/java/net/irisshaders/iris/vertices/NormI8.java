package net.irisshaders.iris.vertices;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

public class NormI8 {
   private static final int X_COMPONENT_OFFSET = 0;
   private static final int Y_COMPONENT_OFFSET = 8;
   private static final int Z_COMPONENT_OFFSET = 16;
   private static final int W_COMPONENT_OFFSET = 24;
   private static final float COMPONENT_RANGE = 127.0F;
   private static final float NORM = 0.007874016F;

   public static int pack(Vector3f normal) {
      return pack(normal.x(), normal.y(), normal.z(), 0.0F);
   }

   public static int pack(Vector3f normal, float w) {
      return pack(normal.x(), normal.y(), normal.z(), w);
   }

   public static int pack(float x, float y, float z, float w) {
      return (int)(x * 127.0F) & 0xFF | ((int)(y * 127.0F) & 0xFF) << 8 | ((int)(z * 127.0F) & 0xFF) << 16 | ((int)(w * 127.0F) & 0xFF) << 24;
   }

   public static byte toByte(float v) {
      return (byte)((byte)(v * 127.0F) & 0xFF);
   }

   public static int packColor(float x, float y, float z, float w) {
      return (int)(x * 127.0F) & 0xFF | ((int)(y * 127.0F) & 0xFF) << 8 | ((int)(z * 127.0F) & 0xFF) << 16 | ((int)w & 0xFF) << 24;
   }

   private static int encode(float comp) {
      return (int)(Mth.clamp(comp, -1.0F, 1.0F) * 127.0F) & 0xFF;
   }

   public static float unpackX(int norm) {
      return (byte)(norm >> 0 & 0xFF) * 0.007874016F;
   }

   public static float unpackY(int norm) {
      return (byte)(norm >> 8 & 0xFF) * 0.007874016F;
   }

   public static float unpackZ(int norm) {
      return (byte)(norm >> 16 & 0xFF) * 0.007874016F;
   }

   public static float unpackW(int norm) {
      return (byte)(norm >> 24 & 0xFF) * 0.007874016F;
   }
}
