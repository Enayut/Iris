package net.irisshaders.iris.gl.shader;

import java.nio.ByteBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public class ShaderWorkarounds {
   public static void safeShaderSource(int glId, CharSequence source) {
      MemoryStack stack = MemoryStack.stackGet();
      int stackPointer = stack.getPointer();

      try {
         ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, true);
         PointerBuffer pointers = stack.mallocPointer(1);
         pointers.put(sourceBuffer);
         GL20C.nglShaderSource(glId, 1, pointers.address0(), 0L);
         APIUtil.apiArrayFree(pointers.address0(), 1);
      } finally {
         stack.setPointer(stackPointer);
      }
   }
}
