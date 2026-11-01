package net.irisshaders.iris.gl.buffer;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.sampler.SamplerLimits;

public class ShaderStorageBufferHolder {
   private static final List<ShaderStorageBuffer> ACTIVE_BUFFERS = new ArrayList<>();
   private int cachedWidth;
   private int cachedHeight;
   private ShaderStorageBuffer[] buffers;
   private boolean destroyed = false;

   public ShaderStorageBufferHolder(Int2ObjectArrayMap<BuiltShaderStorageInfo> overrides, int width, int height) {
      this.cachedWidth = width;
      this.cachedHeight = height;
      this.buffers = new ShaderStorageBuffer[Collections.<Integer>max(overrides.keySet()) + 1];
      overrides.forEach(
         (index, bufferInfo) -> {
            if (bufferInfo.size() > IrisRenderSystem.getVRAM()) {
               throw new ShaderStorageBufferHolder.OutOfVideoMemoryError(
                  "We only have "
                     + toMib(IrisRenderSystem.getVRAM())
                     + "MiB of RAM to work with, but the pack is requesting "
                     + bufferInfo.size()
                     + "! Can't continue."
               );
            }

            if (index > SamplerLimits.get().getMaxShaderStorageUnits()) {
               throw new IllegalStateException(
                  "We don't have enough SSBO units??? (index: " + index + ", max: " + SamplerLimits.get().getMaxShaderStorageUnits()
               );
            }

            this.buffers[index] = new ShaderStorageBuffer(index, bufferInfo);
            ACTIVE_BUFFERS.add(this.buffers[index]);
            int buffer = this.buffers[index].getId();
            if (bufferInfo.relative()) {
               this.buffers[index].resizeIfRelative(width, height);
            } else {
               this.buffers[index].createStatic();
            }
         }
      );
      GlStateManager._glBindBuffer(37074, 0);
   }

   private static long toMib(long x) {
      return x / 1024L / 1024L;
   }

   public static void forceDeleteBuffers() {
      if (!ACTIVE_BUFFERS.isEmpty()) {
         Iris.logger
            .warn(
               "Found "
                  + ACTIVE_BUFFERS.size()
                  + " stored buffers with a total size of "
                  + ACTIVE_BUFFERS.stream().map(ShaderStorageBuffer::getSize).reduce(0L, Long::sum)
                  + ", forcing them to be deleted."
            );
         ACTIVE_BUFFERS.forEach(ShaderStorageBuffer::destroy);
         ACTIVE_BUFFERS.clear();
      }
   }

   public void hasResizedScreen(int width, int height) {
      if (width != this.cachedWidth || height != this.cachedHeight) {
         this.cachedWidth = width;
         this.cachedHeight = height;

         for (ShaderStorageBuffer buffer : this.buffers) {
            if (buffer != null) {
               buffer.resizeIfRelative(width, height);
            }
         }
      }
   }

   public void setupBuffers() {
      if (this.destroyed) {
         throw new IllegalStateException("Tried to use destroyed buffer objects");
      }

      for (ShaderStorageBuffer buffer : this.buffers) {
         if (buffer != null) {
            buffer.bind();
         }
      }
   }

   public int getBufferIndex(int index) {
      if (this.buffers.length >= index && this.buffers[index] != null) {
         return this.buffers[index].getId();
      } else {
         throw new RuntimeException("Tried to query a buffer for indirect dispatch that doesn't exist!");
      }
   }

   public void destroyBuffers() {
      for (ShaderStorageBuffer buffer : this.buffers) {
         if (buffer != null) {
            ACTIVE_BUFFERS.remove(buffer);
            buffer.destroy();
         }
      }

      this.buffers = null;
      this.destroyed = true;
   }

   private static class OutOfVideoMemoryError extends RuntimeException {
      public OutOfVideoMemoryError(String s) {
         super(s);
      }
   }
}
