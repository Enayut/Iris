package net.irisshaders.iris.vertices.sodium;

import net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.irisshaders.iris.vertices.NormalHelper;
import org.lwjgl.system.MemoryUtil;

public class EntityToTerrainVertexSerializer implements VertexSerializer {
   private static final int MIDCOORD = IrisVertexFormats.TERRAIN.getElement("mc_midTexCoord").offset();
   private static final int TANGENT = IrisVertexFormats.TERRAIN.getElement("at_tangent").offset();

   public void serialize(long src, long dst, int vertexCount) {
      int quadCount = vertexCount / 4;

      for (int i = 0; i < quadCount; i++) {
         int normal = MemoryUtil.memGetInt(src + 32L);
         int tangent = NormalHelper.computeTangent(
            null,
            NormI8.unpackX(normal),
            NormI8.unpackY(normal),
            NormI8.unpackZ(normal),
            MemoryUtil.memGetFloat(src),
            MemoryUtil.memGetFloat(src + 4L),
            MemoryUtil.memGetFloat(src + 8L),
            MemoryUtil.memGetFloat(src + 16L),
            MemoryUtil.memGetFloat(src + 20L),
            MemoryUtil.memGetFloat(src + 36L),
            MemoryUtil.memGetFloat(src + 4L + 36L),
            MemoryUtil.memGetFloat(src + 8L + 36L),
            MemoryUtil.memGetFloat(src + 16L + 36L),
            MemoryUtil.memGetFloat(src + 20L + 36L),
            MemoryUtil.memGetFloat(src + 36L + 36L),
            MemoryUtil.memGetFloat(src + 4L + 36L + 36L),
            MemoryUtil.memGetFloat(src + 8L + 36L + 36L),
            MemoryUtil.memGetFloat(src + 16L + 36L + 36L),
            MemoryUtil.memGetFloat(src + 20L + 36L + 36L)
         );
         float midU = 0.0F;
         float midV = 0.0F;

         for (int vertex = 0; vertex < 4; vertex++) {
            midU += MemoryUtil.memGetFloat(src + 16L + 36 * vertex);
            midV += MemoryUtil.memGetFloat(src + 20L + 36 * vertex);
         }

         midU /= 4.0F;
         midV /= 4.0F;

         for (int j = 0; j < 4; j++) {
            MemoryIntrinsics.copyMemory(src, dst, 24);
            MemoryUtil.memPutInt(dst + 24L, MemoryUtil.memGetInt(src + 28L));
            MemoryUtil.memPutInt(dst + 28L, normal);
            MemoryUtil.memPutShort(dst + 32L, (short)CapturedRenderingState.INSTANCE.getCurrentRenderedEntity());
            MemoryUtil.memPutShort(dst + 34L, (short)CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity());
            MemoryUtil.memPutFloat(dst + MIDCOORD, midU);
            MemoryUtil.memPutFloat(dst + MIDCOORD + 4L, midV);
            MemoryUtil.memPutInt(dst + TANGENT, tangent);
            MemoryUtil.memPutInt(dst + 48L, 0);
            src += 36L;
            dst += IrisVertexFormats.TERRAIN.getVertexSize();
         }
      }
   }
}
