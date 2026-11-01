package net.irisshaders.iris.vertices;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import java.util.function.IntFunction;
import net.irisshaders.iris.api.v0.IrisTextVertexSink;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.views.QuadView;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public class IrisTextVertexSinkImpl implements IrisTextVertexSink {
   static final VertexFormat format = IrisVertexFormats.GLYPH;
   private static final int STRIDE = IrisVertexFormats.GLYPH.getVertexSize();
   private static final int OFFSET_POSITION = getOffset("Position");
   private static final int OFFSET_COLOR = getOffset("Color");
   private static final int OFFSET_TEXTURE = getOffset("UV0");
   private static final int OFFSET_MID_TEXTURE = getOffset("mc_midTexCoord");
   private static final int OFFSET_LIGHT = getOffset("UV2");
   private static final int OFFSET_NORMAL = getOffset("Normal");
   private static final int OFFSET_TANGENT = getOffset("at_tangent");
   private final ByteBuffer buffer;
   private final IrisTextVertexSinkImpl.TextQuadView quad = new IrisTextVertexSinkImpl.TextQuadView();
   private final Vector3f saveNormal = new Vector3f();
   private int vertexCount;
   private long elementOffset;
   private float uSum;
   private float vSum;

   public IrisTextVertexSinkImpl(int maxQuadCount, IntFunction<ByteBuffer> buffer) {
      this.buffer = buffer.apply(format.getVertexSize() * 4 * maxQuadCount);
      this.elementOffset = MemoryUtil.memAddress(this.buffer);
   }

   private static int getOffset(String name) {
      VertexFormatElement element = IrisVertexFormats.GLYPH.getElement(name);
      if (element == null) {
         throw new IllegalArgumentException("Cannot find element " + name);
      } else {
         return element.offset();
      }
   }

   @Override
   public VertexFormat getUnderlyingVertexFormat() {
      return format;
   }

   @Override
   public ByteBuffer getUnderlyingByteBuffer() {
      return this.buffer;
   }

   @Override
   public void quad(float minX, float minY, float maxX, float maxY, float z, int color, float minU, float minV, float maxU, float maxV, int light) {
      this.vertex(minX, minY, z, color, minU, minV, light);
      this.vertex(minX, maxY, z, color, minU, maxV, light);
      this.vertex(maxX, maxY, z, color, maxU, maxV, light);
      this.vertex(maxX, minY, z, color, maxU, minV, light);
   }

   private void vertex(float x, float y, float z, int color, float u, float v, int light) {
      this.vertexCount++;
      this.uSum += u;
      this.vSum += v;
      long ptr = this.elementOffset;
      MemoryUtil.memPutFloat(ptr + OFFSET_POSITION, x);
      MemoryUtil.memPutFloat(ptr + OFFSET_POSITION + 4L, y);
      MemoryUtil.memPutFloat(ptr + OFFSET_POSITION + 8L, z);
      MemoryUtil.memPutInt(ptr + OFFSET_COLOR, color);
      MemoryUtil.memPutFloat(ptr + OFFSET_TEXTURE, u);
      MemoryUtil.memPutFloat(ptr + OFFSET_TEXTURE + 4L, v);
      MemoryUtil.memPutInt(ptr + OFFSET_LIGHT, light);
      MemoryUtil.memPutShort(ptr + 32L, (short)CapturedRenderingState.INSTANCE.getCurrentRenderedEntity());
      MemoryUtil.memPutShort(ptr + 34L, (short)CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity());
      MemoryUtil.memPutShort(ptr + 36L, (short)CapturedRenderingState.INSTANCE.getCurrentRenderedItem());
      if (this.vertexCount == 4) {
         this.vertexCount = 0;
         this.uSum *= 0.25F;
         this.vSum *= 0.25F;
         this.quad.setup(this.elementOffset, IrisVertexFormats.GLYPH.getVertexSize());
         NormalHelper.computeFaceNormal(this.saveNormal, this.quad);
         float normalX = this.saveNormal.x;
         float normalY = this.saveNormal.y;
         float normalZ = this.saveNormal.z;
         int normal = NormI8.pack(normalX, normalY, normalZ, 0.0F);
         int tangent = NormalHelper.computeTangent(normalX, normalY, normalZ, this.quad);

         for (long vertex = 0L; vertex < 4L; vertex++) {
            MemoryUtil.memPutFloat(ptr + OFFSET_MID_TEXTURE - STRIDE * vertex, this.uSum);
            MemoryUtil.memPutFloat(ptr + (OFFSET_MID_TEXTURE + 4) - STRIDE * vertex, this.vSum);
            MemoryUtil.memPutInt(ptr + OFFSET_NORMAL - STRIDE * vertex, normal);
            MemoryUtil.memPutInt(ptr + OFFSET_TANGENT - STRIDE * vertex, tangent);
         }

         this.uSum = 0.0F;
         this.vSum = 0.0F;
      }

      this.buffer.position(this.buffer.position() + STRIDE);
      this.elementOffset = this.elementOffset + STRIDE;
   }

   static class TextQuadView implements QuadView {
      long writePointer;
      int stride;

      public TextQuadView() {
      }

      public void setup(long writePointer, int stride) {
         this.writePointer = writePointer;
         this.stride = stride;
      }

      @Override
      public float x(int index) {
         return MemoryUtil.memGetFloat(this.writePointer - this.stride * (3L - index));
      }

      @Override
      public float y(int index) {
         return MemoryUtil.memGetFloat(this.writePointer + 4L - this.stride * (3L - index));
      }

      @Override
      public float z(int index) {
         return MemoryUtil.memGetFloat(this.writePointer + 8L - this.stride * (3L - index));
      }

      @Override
      public float u(int index) {
         return MemoryUtil.memGetFloat(this.writePointer + 16L - this.stride * (3L - index));
      }

      @Override
      public float v(int index) {
         return MemoryUtil.memGetFloat(this.writePointer + 20L - this.stride * (3L - index));
      }
   }
}
