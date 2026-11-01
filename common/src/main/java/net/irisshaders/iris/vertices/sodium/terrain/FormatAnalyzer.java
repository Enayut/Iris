package net.irisshaders.iris.vertices.sodium.terrain;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat.Builder;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;

public class FormatAnalyzer {
   private static final Byte2ObjectMap<ChunkVertexType> classMap = new Byte2ObjectOpenHashMap();

   public static ChunkVertexType createFormat(boolean blockId, boolean normal, boolean midUV, boolean midBlock) {
      byte key = 0;
      if (blockId) {
         key = (byte)(key | 1);
      }

      if (normal) {
         key = (byte)(key | 2);
      }

      if (midUV) {
         key = (byte)(key | 4);
      }

      if (midBlock) {
         key = (byte)(key | 8);
      }

      if (classMap.containsKey(key)) {
         return (ChunkVertexType)classMap.get(key);
      }

      int offset = 20;
      int blockIdOffset;
      if (blockId) {
         blockIdOffset = offset;
         offset += 4;
      } else {
         blockIdOffset = 0;
      }

      int normalOffset;
      if (normal) {
         normalOffset = offset;
         offset += 4;
      } else {
         normalOffset = 0;
      }

      int midUvOffset;
      if (midUV) {
         midUvOffset = offset;
         offset += 4;
      } else {
         midUvOffset = 0;
      }

      int midBlockOffset;
      if (midBlock) {
         midBlockOffset = offset;
         offset += 4;
      } else {
         midBlockOffset = 0;
      }

      Builder VERTEX_FORMAT = VertexFormat.builder(0)
         .addAttribute("a_Position", GpuFormat.RG32_UINT)
         .addAttribute("a_Color", GpuFormat.RGBA8_UNORM)
         .addAttribute("a_TexCoord", GpuFormat.RG16_UINT)
         .addAttribute("a_LightAndData", GpuFormat.RGBA8_UINT);
      if (blockId) {
         VERTEX_FORMAT.addAttribute("mc_Entity", GpuFormat.R32_UINT);
      }

      if (normal) {
         VERTEX_FORMAT.addAttribute("iris_Normal", GpuFormat.R32_UINT);
      }

      if (midUV) {
         VERTEX_FORMAT.addAttribute("mc_midTexCoord", GpuFormat.RG16_UINT);
      }

      if (midBlock) {
         VERTEX_FORMAT.addAttribute("at_midBlock", GpuFormat.RGBA8_SNORM);
      }

      return (ChunkVertexType)classMap.computeIfAbsent(
         key, k -> new XHFPModelVertexType(VERTEX_FORMAT.build(), blockIdOffset, normalOffset, midUvOffset, midBlockOffset)
      );
   }

   static {
      classMap.put((byte)0, ChunkMeshFormats.COMPACT);
   }
}
