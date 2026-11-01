package net.irisshaders.iris.vertices.sodium;

import net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.irisshaders.iris.vertices.NormalHelper;
import org.lwjgl.system.MemoryUtil;

public class ModelToEntityVertexSerializer implements VertexSerializer {
   private static final int MIDCOORD = IrisVertexFormats.ENTITY.getElement("mc_midTexCoord").offset();
   private static final int TANGENT = IrisVertexFormats.ENTITY.getElement("at_tangent").offset();
   private static final int SRC_STRIDE = 36;
   private static final int DST_STRIDE = IrisVertexFormats.ENTITY.getVertexSize();

   public void serialize(long srcBase, long dstBase, int vertexCount) {
      int quadCount = vertexCount >> 2;
      short entity = (short)CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
      short blockEntity = (short)CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
      short item = (short)CapturedRenderingState.INSTANCE.getCurrentRenderedItem();
      long src = srcBase;
      long dst = dstBase;

      for (int q = 0; q < quadCount; q++) {
         long v0 = src;
         long v1 = src + 36L;
         long v2 = v1 + 36L;
         long v3 = v2 + 36L;
         int packedNormal = MemoryUtil.memGetInt(v0 + 32L);
         float nx = NormI8.unpackX(packedNormal);
         float ny = NormI8.unpackY(packedNormal);
         float nz = NormI8.unpackZ(packedNormal);
         float v0x = MemoryUtil.memGetFloat(v0);
         float v0y = MemoryUtil.memGetFloat(v0 + 4L);
         float v0z = MemoryUtil.memGetFloat(v0 + 8L);
         float v0u = MemoryUtil.memGetFloat(v0 + 16L);
         float v0v = MemoryUtil.memGetFloat(v0 + 20L);
         float v1x = MemoryUtil.memGetFloat(v1);
         float v1y = MemoryUtil.memGetFloat(v1 + 4L);
         float v1z = MemoryUtil.memGetFloat(v1 + 8L);
         float v1u = MemoryUtil.memGetFloat(v1 + 16L);
         float v1v = MemoryUtil.memGetFloat(v1 + 20L);
         float v2x = MemoryUtil.memGetFloat(v2);
         float v2y = MemoryUtil.memGetFloat(v2 + 4L);
         float v2z = MemoryUtil.memGetFloat(v2 + 8L);
         float v2u = MemoryUtil.memGetFloat(v2 + 16L);
         float v2v = MemoryUtil.memGetFloat(v2 + 20L);
         int tangent = NormalHelper.computeTangent(null, nx, ny, nz, v0x, v0y, v0z, v0u, v0v, v1x, v1y, v1z, v1u, v1v, v2x, v2y, v2z, v2u, v2v);
         float midU = (v0u + v1u + v2u + MemoryUtil.memGetFloat(v3 + 16L)) * 0.25F;
         float midV = (v0v + v1v + v2v + MemoryUtil.memGetFloat(v3 + 20L)) * 0.25F;
         long writeSrc = v0;
         long writeDst = dst;

         for (int i = 0; i < 4; i++) {
            MemoryIntrinsics.copyMemory(writeSrc, writeDst, 36);
            MemoryUtil.memPutShort(writeDst + 36L, entity);
            MemoryUtil.memPutShort(writeDst + 38L, blockEntity);
            MemoryUtil.memPutShort(writeDst + 40L, item);
            MemoryUtil.memPutFloat(writeDst + MIDCOORD, midU);
            MemoryUtil.memPutFloat(writeDst + MIDCOORD + 4L, midV);
            MemoryUtil.memPutInt(writeDst + TANGENT, tangent);
            writeSrc += 36L;
            writeDst += DST_STRIDE;
         }

         src += 144L;
         dst += DST_STRIDE * 4;
      }
   }
}
