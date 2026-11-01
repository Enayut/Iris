package net.irisshaders.iris.vertices.sodium;

import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializer;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import org.lwjgl.system.MemoryUtil;

public class IrisEntityToTerrainVertexSerializer implements VertexSerializer {
   public void serialize(long src, long dst, int vertexCount) {
      for (int vertexIndex = 0; vertexIndex < vertexCount; vertexIndex++) {
         MemoryUtil.memPutFloat(dst, MemoryUtil.memGetFloat(src));
         MemoryUtil.memPutFloat(dst + 4L, MemoryUtil.memGetFloat(src + 4L));
         MemoryUtil.memPutFloat(dst + 8L, MemoryUtil.memGetFloat(src + 8L));
         MemoryUtil.memPutInt(dst + 12L, MemoryUtil.memGetInt(src + 12L));
         MemoryUtil.memPutFloat(dst + 16L, MemoryUtil.memGetFloat(src + 16L));
         MemoryUtil.memPutFloat(dst + 20L, MemoryUtil.memGetFloat(src + 20L));
         MemoryUtil.memPutInt(dst + 24L, MemoryUtil.memGetInt(src + 28L));
         MemoryUtil.memPutInt(dst + 28L, MemoryUtil.memGetInt(src + 32L));
         MemoryUtil.memPutInt(dst + 32L, 0);
         MemoryUtil.memPutInt(dst + 36L, MemoryUtil.memGetInt(src + 36L));
         MemoryUtil.memPutInt(dst + 40L, MemoryUtil.memGetInt(src + 40L));
         MemoryUtil.memPutInt(dst + 44L, MemoryUtil.memGetInt(src + 44L));
         src += IrisVertexFormats.ENTITY.getVertexSize();
         dst += IrisVertexFormats.TERRAIN.getVertexSize();
      }
   }
}
