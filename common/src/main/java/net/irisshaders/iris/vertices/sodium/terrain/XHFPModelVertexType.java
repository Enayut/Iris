package net.irisshaders.iris.vertices.sodium.terrain;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;

public class XHFPModelVertexType implements ChunkVertexType {
   private static final int POSITION_MAX_VALUE = 65536;
   private static final int TEXTURE_MAX_VALUE = 32768;
   private static final float MODEL_ORIGIN = 8.0F;
   private static final float MODEL_RANGE = 32.0F;
   private static final float MODEL_SCALE = 4.8828125E-4F;
   private static final float MODEL_SCALE_INV = 2048.0F;
   private static final float TEXTURE_SCALE = 3.0517578E-5F;
   private final VertexFormat format;
   private final int normalOffset;
   private final int blockIdOffset;
   private final int midBlockOffset;
   private final int midUvOffset;

   public XHFPModelVertexType(VertexFormat format, int blockIdOffset, int normalOffset, int midUvOffset, int midBlockOffset) {
      this.format = format;
      this.blockIdOffset = blockIdOffset;
      this.normalOffset = normalOffset;
      this.midUvOffset = midUvOffset;
      this.midBlockOffset = midBlockOffset;
   }

   public static int encodeOld(float u, float v) {
      return (Math.round(u * 32768.0F) & 65535) << 0 | (Math.round(v * 32768.0F) & 65535) << 16;
   }

   public VertexFormat getVertexFormat() {
      return this.format;
   }

   public ChunkVertexEncoder getEncoder() {
      return new XHFPTerrainVertex(this.blockIdOffset, this.normalOffset, this.midUvOffset, this.midBlockOffset, this.format.getVertexSize());
   }
}
