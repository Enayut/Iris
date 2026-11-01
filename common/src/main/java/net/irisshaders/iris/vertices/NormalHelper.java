package net.irisshaders.iris.vertices;

import net.irisshaders.iris.vertices.views.QuadView;
import net.irisshaders.iris.vertices.views.TriView;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

public abstract class NormalHelper {
   private static final float EPS = 1.0E-20F;

   private NormalHelper() {
   }

   public static int invertPackedNormal(int packed) {
      int ix = -(packed & 0xFF);
      int iy = -(packed >> 8 & 0xFF);
      int iz = -(packed >> 16 & 0xFF);
      ix &= 255;
      iy &= 255;
      iz &= 255;
      return packed & 0xFF000000 | iz << 16 | iy << 8 | ix;
   }

   public static void computeFaceNormal(@NotNull Vector3f saveTo, QuadView q) {
      float x0 = q.x(0);
      float y0 = q.y(0);
      float z0 = q.z(0);
      float x1 = q.x(1);
      float y1 = q.y(1);
      float z1 = q.z(1);
      float x2 = q.x(2);
      float y2 = q.y(2);
      float z2 = q.z(2);
      float x3 = q.x(3);
      float y3 = q.y(3);
      float z3 = q.z(3);
      computeFaceNormalManual(saveTo, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
   }

   public static void computeFaceNormalManual(
      @NotNull Vector3f saveTo, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3
   ) {
      float dx0 = x2 - x0;
      float dy0 = y2 - y0;
      float dz0 = z2 - z0;
      float dx1 = x3 - x1;
      float dy1 = y3 - y1;
      float dz1 = z3 - z1;
      float normX = dy0 * dz1 - dz0 * dy1;
      float normY = dz0 * dx1 - dx0 * dz1;
      float normZ = dx0 * dy1 - dy0 * dx1;
      saveTo.set(normX, normY, normZ);
      saveTo.normalize();
   }

   public static void computeFaceNormalFlipped(@NotNull Vector3f saveTo, QuadView q) {
      float x0 = q.x(3);
      float y0 = q.y(3);
      float z0 = q.z(3);
      float x1 = q.x(2);
      float y1 = q.y(2);
      float z1 = q.z(2);
      float x2 = q.x(1);
      float y2 = q.y(1);
      float z2 = q.z(1);
      float x3 = q.x(0);
      float y3 = q.y(0);
      float z3 = q.z(0);
      computeFaceNormalManual(saveTo, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
   }

   public static void computeFaceNormalTri(@NotNull Vector3f saveTo, TriView t) {
      float x0 = t.x(0);
      float y0 = t.y(0);
      float z0 = t.z(0);
      float x1 = t.x(1);
      float y1 = t.y(1);
      float z1 = t.z(1);
      float x2 = t.x(2);
      float y2 = t.y(2);
      float z2 = t.z(2);
      computeFaceNormalManual(saveTo, x0, y0, z0, x1, y1, z1, x2, y2, z2, x0, y0, z0);
   }

   public static int computeTangentSmooth(float normalX, float normalY, float normalZ, TriView t) {
      float x0 = t.x(0);
      float y0 = t.y(0);
      float z0 = t.z(0);
      float x1 = t.x(1);
      float y1 = t.y(1);
      float z1 = t.z(1);
      float x2 = t.x(2);
      float y2 = t.y(2);
      float z2 = t.z(2);
      float d0 = x0 * normalX + y0 * normalY + z0 * normalZ;
      float d1 = x1 * normalX + y1 * normalY + z1 * normalZ;
      float d2 = x2 * normalX + y2 * normalY + z2 * normalZ;
      x0 -= d0 * normalX;
      y0 -= d0 * normalY;
      z0 -= d0 * normalZ;
      x1 -= d1 * normalX;
      y1 -= d1 * normalY;
      z1 -= d1 * normalZ;
      x2 -= d2 * normalX;
      y2 -= d2 * normalY;
      z2 -= d2 * normalZ;
      float edge1x = x1 - x0;
      float edge1y = y1 - y0;
      float edge1z = z1 - z0;
      float edge2x = x2 - x0;
      float edge2y = y2 - y0;
      float edge2z = z2 - z0;
      float u0 = t.u(0);
      float v0 = t.v(0);
      float u1 = t.u(1);
      float v1 = t.v(1);
      float u2 = t.u(2);
      float v2 = t.v(2);
      float deltaU1 = u1 - u0;
      float deltaV1 = v1 - v0;
      float deltaU2 = u2 - u0;
      float deltaV2 = v2 - v0;
      float fdenom = deltaU1 * deltaV2 - deltaU2 * deltaV1;
      float f;
      if (fdenom == 0.0) {
         f = 1.0F;
      } else {
         f = 1.0F / fdenom;
      }

      float tangentx = f * (deltaV2 * edge1x - deltaV1 * edge2x);
      float tangenty = f * (deltaV2 * edge1y - deltaV1 * edge2y);
      float tangentz = f * (deltaV2 * edge1z - deltaV1 * edge2z);
      float tcoeff = rsqrt(tangentx * tangentx + tangenty * tangenty + tangentz * tangentz);
      tangentx *= tcoeff;
      tangenty *= tcoeff;
      tangentz *= tcoeff;
      float bitangentx = f * (-deltaU2 * edge1x + deltaU1 * edge2x);
      float bitangenty = f * (-deltaU2 * edge1y + deltaU1 * edge2y);
      float bitangentz = f * (-deltaU2 * edge1z + deltaU1 * edge2z);
      float bitcoeff = rsqrt(bitangentx * bitangentx + bitangenty * bitangenty + bitangentz * bitangentz);
      bitangentx *= bitcoeff;
      bitangenty *= bitcoeff;
      bitangentz *= bitcoeff;
      float pbitangentx = tangenty * normalZ - tangentz * normalY;
      float pbitangenty = tangentz * normalX - tangentx * normalZ;
      float pbitangentz = tangentx * normalY - tangenty * normalX;
      float dot = bitangentx * pbitangentx + bitangenty * pbitangenty + bitangentz * pbitangentz;
      float tangentW;
      if (dot < 0.0F) {
         tangentW = -1.0F;
      } else {
         tangentW = 1.0F;
      }

      return NormI8.pack(tangentx, tangenty, tangentz, tangentW);
   }

   public static int computeTangent(float normalX, float normalY, float normalZ, TriView t) {
      float x0 = t.x(0);
      float y0 = t.y(0);
      float z0 = t.z(0);
      float x1 = t.x(1);
      float y1 = t.y(1);
      float z1 = t.z(1);
      float x2 = t.x(2);
      float y2 = t.y(2);
      float z2 = t.z(2);
      float edge1x = x1 - x0;
      float edge1y = y1 - y0;
      float edge1z = z1 - z0;
      float edge2x = x2 - x0;
      float edge2y = y2 - y0;
      float edge2z = z2 - z0;
      float u0 = t.u(0);
      float v0 = t.v(0);
      float u1 = t.u(1);
      float v1 = t.v(1);
      float u2 = t.u(2);
      float v2 = t.v(2);
      float deltaU1 = u1 - u0;
      float deltaV1 = v1 - v0;
      float deltaU2 = u2 - u0;
      float deltaV2 = v2 - v0;
      float fdenom = deltaU1 * deltaV2 - deltaU2 * deltaV1;
      float f;
      if (fdenom == 0.0) {
         f = 1.0F;
      } else {
         f = 1.0F / fdenom;
      }

      float tangentx = f * (deltaV2 * edge1x - deltaV1 * edge2x);
      float tangenty = f * (deltaV2 * edge1y - deltaV1 * edge2y);
      float tangentz = f * (deltaV2 * edge1z - deltaV1 * edge2z);
      float tcoeff = rsqrt(tangentx * tangentx + tangenty * tangenty + tangentz * tangentz);
      tangentx *= tcoeff;
      tangenty *= tcoeff;
      tangentz *= tcoeff;
      float bitangentx = f * (-deltaU2 * edge1x + deltaU1 * edge2x);
      float bitangenty = f * (-deltaU2 * edge1y + deltaU1 * edge2y);
      float bitangentz = f * (-deltaU2 * edge1z + deltaU1 * edge2z);
      float bitcoeff = rsqrt(bitangentx * bitangentx + bitangenty * bitangenty + bitangentz * bitangentz);
      bitangentx *= bitcoeff;
      bitangenty *= bitcoeff;
      bitangentz *= bitcoeff;
      float pbitangentx = tangenty * normalZ - tangentz * normalY;
      float pbitangenty = tangentz * normalX - tangentx * normalZ;
      float pbitangentz = tangentx * normalY - tangenty * normalX;
      float dot = bitangentx * pbitangentx + bitangenty * pbitangenty + bitangentz * pbitangentz;
      float tangentW;
      if (dot < 0.0F) {
         tangentW = -1.0F;
      } else {
         tangentW = 1.0F;
      }

      return NormI8.pack(tangentx, tangenty, tangentz, tangentW);
   }

   public static int computeTangent(
      float normalX,
      float normalY,
      float normalZ,
      float x0,
      float y0,
      float z0,
      float u0,
      float v0,
      float x1,
      float y1,
      float z1,
      float u1,
      float v1,
      float x2,
      float y2,
      float z2,
      float u2,
      float v2
   ) {
      return computeTangent(null, normalX, normalY, normalZ, x0, y0, z0, u0, v0, x1, y1, z1, u1, v1, x2, y2, z2, u2, v2);
   }

   public static int computeTangent(
      Vector4f output,
      float normalX,
      float normalY,
      float normalZ,
      float x0,
      float y0,
      float z0,
      float u0,
      float v0,
      float x1,
      float y1,
      float z1,
      float u1,
      float v1,
      float x2,
      float y2,
      float z2,
      float u2,
      float v2
   ) {
      float edge1x = x1 - x0;
      float edge1y = y1 - y0;
      float edge1z = z1 - z0;
      float edge2x = x2 - x0;
      float edge2y = y2 - y0;
      float edge2z = z2 - z0;
      float deltaU1 = u1 - u0;
      float deltaV1 = v1 - v0;
      float deltaU2 = u2 - u0;
      float deltaV2 = v2 - v0;
      float fdenom = deltaU1 * deltaV2 - deltaU2 * deltaV1;
      float f;
      if (fdenom == 0.0) {
         f = 1.0F;
      } else {
         f = 1.0F / fdenom;
      }

      float tangentx = f * (deltaV2 * edge1x - deltaV1 * edge2x);
      float tangenty = f * (deltaV2 * edge1y - deltaV1 * edge2y);
      float tangentz = f * (deltaV2 * edge1z - deltaV1 * edge2z);
      float tcoeff = rsqrt(tangentx * tangentx + tangenty * tangenty + tangentz * tangentz);
      tangentx *= tcoeff;
      tangenty *= tcoeff;
      tangentz *= tcoeff;
      if (tangentx == 0.0F && tangenty == 0.0F && tangentz == 0.0F) {
         return -1;
      }

      float bitangentx = f * (-deltaU2 * edge1x + deltaU1 * edge2x);
      float bitangenty = f * (-deltaU2 * edge1y + deltaU1 * edge2y);
      float bitangentz = f * (-deltaU2 * edge1z + deltaU1 * edge2z);
      float bitcoeff = rsqrt(bitangentx * bitangentx + bitangenty * bitangenty + bitangentz * bitangentz);
      bitangentx *= bitcoeff;
      bitangenty *= bitcoeff;
      bitangentz *= bitcoeff;
      float pbitangentx = tangenty * normalZ - tangentz * normalY;
      float pbitangenty = tangentz * normalX - tangentx * normalZ;
      float pbitangentz = tangentx * normalY - tangenty * normalX;
      float dot = bitangentx * pbitangentx + bitangenty * pbitangenty + bitangentz * pbitangentz;
      float tangentW;
      if (dot < 0.0F) {
         tangentW = -1.0F;
      } else {
         tangentW = 1.0F;
      }

      if (output != null) {
         output.set(tangentx, tangenty, tangentz, tangentW);
      }

      return NormI8.pack(tangentx, tangenty, tangentz, tangentW);
   }

   private static float rsqrt(float value) {
      return value == 0.0F ? 1.0F : (float)(1.0 / Math.sqrt(value));
   }

   private static int snorm12(float v) {
      float c = Math.max(-1.0F, Math.min(1.0F, v));
      int q = Math.round(c * 2047.0F);
      if (q == -2048) {
         q = -2047;
      }

      return q;
   }

   private static float signNotZero(float v) {
      return v >= 0.0F ? 1.0F : -1.0F;
   }

   public static int encodeNormal(float x, float y, float z) {
      float rev = 1.0F / (Math.abs(x) + Math.abs(y) + Math.abs(z));
      float pX = x * rev;
      float pY = y * rev;
      float outX;
      float outY;
      if (z > 0.0F) {
         outX = pX;
         outY = pY;
      } else {
         outX = (1.0F - Math.abs(pY)) * signNotZero(pX);
         outY = (1.0F - Math.abs(pX)) * signNotZero(pY);
      }

      int qx = snorm12(outX);
      int qy = snorm12(outY);
      return (qx & 4095) << 12 | qy & 4095;
   }

   private static void onbFromUnitNormal(float nx, float ny, float nz, Vector3f t1, Vector3f t2) {
      float s = nz >= 0.0F ? 1.0F : -1.0F;
      float a = -1.0F / (s + nz);
      float b = nx * ny * a;
      t1.set(1.0F + s * nx * nx * a, s * b, -s * nx).normalize();
      t2.set(ny * t1.z - nz * t1.y, nz * t1.x - nx * t1.z, nx * t1.y - ny * t1.x);
   }

   private static float encodeDiamond(float px, float py) {
      float denom = Math.abs(px) + Math.abs(py);
      if (denom <= 1.0E-20F) {
         return 0.5F;
      }

      float x = px / denom;
      float pys = py >= 0.0F ? 1.0F : -1.0F;
      return -pys * 0.25F * x + 0.5F + pys * 0.25F;
   }

   public static int packDiamondByte(Vector3fc normal, Vector3fc tangent, Vector3f t1, Vector3f t2, Vector3f tp) {
      t2.set(normal).normalize();
      float nx = t2.x;
      float ny = t2.y;
      float nz = t2.z;
      onbFromUnitNormal(nx, ny, nz, t1, t2);
      float NdT = nx * tangent.x() + ny * tangent.y() + nz * tangent.z();
      tp.set(tangent).sub(nx * NdT, ny * NdT, nz * NdT);
      if (tp.lengthSquared() > 1.0E-20F) {
         tp.normalize();
      } else {
         tp.set(t1);
      }

      float px = tp.dot(t1);
      float py = tp.dot(t2);
      float d = encodeDiamond(px, py);
      int q = Math.min(256, Math.max(0, Math.round(d * 256.0F)));
      return q & 0xFF;
   }

   public static int encodeNormalTangent(Vector3f normal, Vector3f tangent, Vector3f scratch1, Vector3f scratch2, Vector3f scratchOut) {
      int encodedNormal = encodeNormal(normal.x, normal.y, normal.z);
      int encodedTangent = packDiamondByte(normal, tangent, scratch1, scratch2, scratchOut);
      return encodedTangent << 24 | encodedNormal;
   }
}
