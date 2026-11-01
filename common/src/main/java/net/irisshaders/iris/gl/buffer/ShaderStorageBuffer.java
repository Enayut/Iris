package net.irisshaders.iris.gl.buffer;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.nio.ByteBuffer;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.IrisRenderSystem;
import org.lwjgl.system.MemoryUtil;

public class ShaderStorageBuffer {
   protected final int index;
   protected final BuiltShaderStorageInfo info;
   protected final ByteBuffer content;
   protected int id = IrisRenderSystem.createBuffers();

   public ShaderStorageBuffer(int index, BuiltShaderStorageInfo info) {
      if (info.content() != null) {
         this.content = MemoryUtil.memAlloc(info.content().length);
         this.content.put(info.content());
         this.content.flip();
      } else {
         this.content = null;
      }

      GLDebug.nameObject(33504, this.id, "SSBO " + index);
      this.index = index;
      this.info = info;
   }

   public final int getIndex() {
      return this.index;
   }

   public final long getSize() {
      return this.info.size();
   }

   protected void destroy() {
      IrisRenderSystem.bindBufferBase(37074, this.index, 0);
      IrisRenderSystem.deleteBuffers(this.id);
      MemoryUtil.memFree(this.content);
   }

   public void bind() {
      IrisRenderSystem.bindBufferBase(37074, this.index, this.id);
   }

   public void resizeIfRelative(int width, int height) {
      if (this.info.relative()) {
         IrisRenderSystem.deleteBuffers(this.id);
         int newId = GlStateManager._glGenBuffers();
         GlStateManager._glBindBuffer(37074, newId);
         long newWidth = (long)(width * this.info.scaleX());
         long newHeight = (long)(height * this.info.scaleY());
         long finalSize = newHeight * newWidth * this.info.size();
         IrisRenderSystem.bufferStorage(37074, finalSize, 0);
         IrisRenderSystem.clearBufferSubData(37074, 33321, 0L, finalSize, 6403, 5120, new int[]{0});
         IrisRenderSystem.bindBufferBase(37074, this.index, newId);
         this.id = newId;
      }
   }

   public int getId() {
      return this.id;
   }

   public void createStatic() {
      GlStateManager._glBindBuffer(37074, this.getId());
      IrisRenderSystem.bufferStorage(37074, this.info.size(), this.content == null ? 0 : 256);
      if (this.content != null) {
         GlStateManager._glBufferSubData(37074, 0L, this.content);
      } else {
         IrisRenderSystem.clearBufferSubData(37074, 33321, 0L, this.info.size(), 6403, 5120, new int[]{0});
      }

      this.bind();
   }
}
