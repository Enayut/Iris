package net.irisshaders.iris.shaderpack;

import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.shaderpack.properties.IndirectPointer;

public record FilledIndirectPointer(int buffer, long offset) {
   public static FilledIndirectPointer basedOff(ShaderStorageBufferHolder holder, IndirectPointer pointer) {
      return pointer != null && holder != null ? new FilledIndirectPointer(holder.getBufferIndex(pointer.buffer()), pointer.offset()) : null;
   }
}
