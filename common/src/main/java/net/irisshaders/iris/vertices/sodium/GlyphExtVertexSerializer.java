package net.irisshaders.iris.vertices.sodium;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.irisshaders.iris.vertices.NormI8;
import net.irisshaders.iris.vertices.NormalHelper;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public class GlyphExtVertexSerializer implements VertexSerializer {
   private static final int OFFSET_POSITION = 0;
   private static final int OFFSET_MID_TEXTURE = IrisVertexFormats.GLYPH.getElement("mc_midTexCoord").offset();
   private static final int OFFSET_COLOR = DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR.getElement("Color").offset();
   private static final int OFFSET_TEXTURE = DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR.getElement("UV0").offset();
   private static final int OFFSET_LIGHT = DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR.getElement("UV2").offset();
   private static final int OFFSET_NORMAL = IrisVertexFormats.GLYPH.getElement("Normal").offset();
   private static final int OFFSET_TANGENT = IrisVertexFormats.GLYPH.getElement("at_tangent").offset();
   private static final QuadViewEntity quad = new QuadViewEntity();
   private static final Vector3f saveNormal = new Vector3f();
   private static final int STRIDE = IrisVertexFormats.GLYPH.getVertexSize();

   private static void endQuad(float uSum, float vSum, long src, long dst) {
      uSum *= 0.25F;
      vSum *= 0.25F;
      quad.setup(dst, IrisVertexFormats.GLYPH.getVertexSize());
      NormalHelper.computeFaceNormal(saveNormal, quad);
      float normalX = saveNormal.x;
      float normalY = saveNormal.y;
      float normalZ = saveNormal.z;
      int normal = NormI8.pack(saveNormal);
      int tangent = NormalHelper.computeTangent(normalX, normalY, normalZ, quad);

      for (long vertex = 0L; vertex < 4L; vertex++) {
         MemoryUtil.memPutFloat(dst + OFFSET_MID_TEXTURE - STRIDE * vertex, uSum);
         MemoryUtil.memPutFloat(dst + (OFFSET_MID_TEXTURE + 4) - STRIDE * vertex, vSum);
         MemoryUtil.memPutInt(dst + OFFSET_NORMAL - STRIDE * vertex, normal);
         MemoryUtil.memPutInt(dst + OFFSET_TANGENT - STRIDE * vertex, tangent);
      }
   }

   public void serialize(long src, long dst, int vertexCount) {
      float uSum = 0.0F;
      float vSum = 0.0F;

      for (int i = 0; i < vertexCount; i++) {
         float u = MemoryUtil.memGetFloat(src + OFFSET_TEXTURE);
         float v = MemoryUtil.memGetFloat(src + OFFSET_TEXTURE + 4L);
         uSum += u;
         vSum += v;
         MemoryIntrinsics.copyMemory(src, dst, 28);
         MemoryUtil.memPutShort(dst + 32L, (short)CapturedRenderingState.INSTANCE.getCurrentRenderedEntity());
         MemoryUtil.memPutShort(dst + 34L, (short)CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity());
         MemoryUtil.memPutShort(dst + 36L, (short)CapturedRenderingState.INSTANCE.getCurrentRenderedItem());
         if (i != 3) {
            src += DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR.getVertexSize();
            dst += IrisVertexFormats.GLYPH.getVertexSize();
         }
      }

      endQuad(uSum, vSum, src, dst);
   }
}
