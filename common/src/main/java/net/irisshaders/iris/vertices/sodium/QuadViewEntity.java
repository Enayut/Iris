package net.irisshaders.iris.vertices.sodium;

import net.irisshaders.iris.vertices.views.QuadView;
import org.lwjgl.system.MemoryUtil;

public class QuadViewEntity implements QuadView {
   private long writePointer;
   private int stride;

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
