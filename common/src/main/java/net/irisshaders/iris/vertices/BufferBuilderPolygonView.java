package net.irisshaders.iris.vertices;

import net.irisshaders.iris.vertices.views.QuadView;
import org.lwjgl.system.MemoryUtil;

public class BufferBuilderPolygonView implements QuadView {
   private long[] writeOffsets;
   private long pointer;
   private int positionOffset;
   private int uvOffset;

   public void setup(long pointer, long[] writeOffsets, int positionOffset, int uvOffset) {
      this.pointer = pointer;
      this.writeOffsets = writeOffsets;
      this.positionOffset = positionOffset;
      this.uvOffset = uvOffset;
   }

   @Override
   public float x(int index) {
      return MemoryUtil.memGetFloat(this.pointer + this.writeOffsets[index] + this.positionOffset);
   }

   @Override
   public float y(int index) {
      return MemoryUtil.memGetFloat(this.pointer + this.writeOffsets[index] + this.positionOffset + 4L);
   }

   @Override
   public float z(int index) {
      return MemoryUtil.memGetFloat(this.pointer + this.writeOffsets[index] + this.positionOffset + 8L);
   }

   @Override
   public float u(int index) {
      return MemoryUtil.memGetFloat(this.pointer + this.writeOffsets[index] + this.uvOffset);
   }

   @Override
   public float v(int index) {
      return MemoryUtil.memGetFloat(this.pointer + this.writeOffsets[index] + this.uvOffset + 4L);
   }
}
