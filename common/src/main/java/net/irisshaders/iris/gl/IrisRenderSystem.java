package net.irisshaders.iris.gl;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.backend.opengl.GlDevice;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.mixin.GlStateManagerAccessor;
import net.irisshaders.iris.mixin.GpuDeviceAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.EXTShaderImageLoadStore;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GL46C;

public class IrisRenderSystem {
   private static final int[] emptyArray = new int[SamplerLimits.get().getMaxTextureUnits()];
   private static GpuBufferSlice backupProjection;
   private static ProjectionMatrixBuffer perspectiveProjectionMatrixBuffer;
   private static ProjectionType backupProjectionType;
   private static IrisRenderSystem.DSAAccess dsaState;
   private static boolean hasMultibind;
   private static boolean supportsCompute;
   private static boolean supportsTesselation;
   private static int polygonMode = 6914;
   private static int backupPolygonMode = 6914;
   private static int[] samplers;
   private static final IntList textureToUnswizzle = new IntArrayList();
   private static int lastTex = -1;
   private static boolean cullingState;

   public static void initRenderer() {
      if (GL.getCapabilities().OpenGL45) {
         dsaState = new IrisRenderSystem.DSACore();
         Iris.logger.info("OpenGL 4.5 detected, enabling DSA.");
      } else if (GL.getCapabilities().GL_ARB_direct_state_access) {
         dsaState = new IrisRenderSystem.DSAARB();
         Iris.logger.info("ARB_direct_state_access detected, enabling DSA.");
      } else {
         dsaState = new IrisRenderSystem.DSAUnsupported();
         Iris.logger.info("DSA support not detected.");
      }

      hasMultibind = GL.getCapabilities().OpenGL45 || GL.getCapabilities().GL_ARB_multi_bind;
      perspectiveProjectionMatrixBuffer = new ProjectionMatrixBuffer("Iris shadow map projection");
      supportsCompute = GL.getCapabilities().glDispatchCompute != 0L;
      supportsTesselation = GL.getCapabilities().GL_ARB_tessellation_shader || GL.getCapabilities().OpenGL40;
      samplers = new int[SamplerLimits.get().getMaxTextureUnits()];
   }

   public static void getIntegerv(int pname, int[] params) {
      RenderSystem.assertOnRenderThread();
      GL32C.glGetIntegerv(pname, params);
   }

   public static void getFloatv(int pname, float[] params) {
      RenderSystem.assertOnRenderThread();
      GL32C.glGetFloatv(pname, params);
   }

   public static void generateMipmaps(int texture, int mipmapTarget) {
      RenderSystem.assertOnRenderThread();
      dsaState.generateMipmaps(texture, mipmapTarget);
   }

   public static void bindAttributeLocation(int program, int index, CharSequence name) {
      RenderSystem.assertOnRenderThread();
      GL32C.glBindAttribLocation(program, index, name);
   }

   public static void texImage1D(
      int texture, int target, int level, int internalformat, int width, int border, int format, int type, @Nullable ByteBuffer pixels
   ) {
      RenderSystem.assertOnRenderThread();
      bindTextureForSetup(target, texture);
      GL30C.glTexImage1D(target, level, internalformat, width, border, format, type, pixels);
   }

   public static void texImage2D(
      int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels
   ) {
      RenderSystem.assertOnRenderThread();
      bindTextureForSetup(target, texture);
      GL32C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
   }

   public static void texImage3D(
      int texture, int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, @Nullable ByteBuffer pixels
   ) {
      RenderSystem.assertOnRenderThread();
      bindTextureForSetup(target, texture);
      GL30C.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
   }

   public static void uniformMatrix4fv(int location, boolean transpose, FloatBuffer matrix) {
      RenderSystem.assertOnRenderThread();
      GL32C.glUniformMatrix4fv(location, transpose, matrix);
   }

   public static void uniformMatrix4fv(int location, boolean transpose, float[] matrix) {
      RenderSystem.assertOnRenderThread();
      GL32C.glUniformMatrix4fv(location, transpose, matrix);
   }

   public static void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
      RenderSystem.assertOnRenderThread();
      GL32C.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
   }

   public static void uniform1f(int location, float v0) {
      RenderSystem.assertOnRenderThread();
      GL32C.glUniform1f(location, v0);
   }

   public static void uniform2f(int location, float v0, float v1) {
      RenderSystem.assertOnRenderThread();
      GL32C.glUniform2f(location, v0, v1);
   }

   public static void uniform2i(int location, int v0, int v1) {
      RenderSystem.assertOnRenderThread();
      GL32C.glUniform2i(location, v0, v1);
   }

   public static void uniform3f(int location, float v0, float v1, float v2) {
      RenderSystem.assertOnRenderThread();
      GL32C.glUniform3f(location, v0, v1, v2);
   }

   public static void uniform3i(int location, int v0, int v1, int v2) {
      RenderSystem.assertOnRenderThread();
      GL32C.glUniform3i(location, v0, v1, v2);
   }

   public static void uniform4f(int location, float v0, float v1, float v2, float v3) {
      RenderSystem.assertOnRenderThread();
      GL32C.glUniform4f(location, v0, v1, v2, v3);
   }

   public static void uniform4i(int location, int v0, int v1, int v2, int v3) {
      RenderSystem.assertOnRenderThread();
      GL32C.glUniform4i(location, v0, v1, v2, v3);
   }

   public static void texParameteriv(int texture, int target, int pname, int[] params) {
      RenderSystem.assertOnRenderThread();
      dsaState.texParameteriv(texture, target, pname, params);
   }

   public static void texParameterivDirect(int target, int pname, int[] params) {
      RenderSystem.assertOnRenderThread();
      GL32C.glTexParameteriv(target, pname, params);
   }

   public static void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
      dsaState.copyTexSubImage2D(destTexture, target, i, i1, i2, i3, i4, width, height);
   }

   public static void texParameteri(int texture, int target, int pname, int param) {
      RenderSystem.assertOnRenderThread();
      dsaState.texParameteri(texture, target, pname, param);
   }

   public static void texParameterf(int texture, int target, int pname, float param) {
      RenderSystem.assertOnRenderThread();
      dsaState.texParameterf(texture, target, pname, param);
   }

   public static String getProgramInfoLog(int program) {
      RenderSystem.assertOnRenderThread();
      return GL32C.glGetProgramInfoLog(program);
   }

   public static String getShaderInfoLog(int shader) {
      RenderSystem.assertOnRenderThread();
      return GL32C.glGetShaderInfoLog(shader);
   }

   public static void drawBuffers(int framebuffer, int[] buffers) {
      RenderSystem.assertOnRenderThread();
      dsaState.drawBuffers(framebuffer, buffers);
   }

   public static void readBuffer(int framebuffer, int buffer) {
      RenderSystem.assertOnRenderThread();
      dsaState.readBuffer(framebuffer, buffer);
   }

   public static void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values) {
      RenderSystem.assertOnRenderThread();
      dsaState.clearBufferfv(framebuffer, buffer, drawbuffer, values);
   }

   public static void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
      RenderSystem.assertOnRenderThread();
      dsaState.clearBufferiv(framebuffer, buffer, drawbuffer, values);
   }

   public static void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
      RenderSystem.assertOnRenderThread();
      dsaState.clearBufferuiv(framebuffer, buffer, drawbuffer, values);
   }

   public static String getActiveUniform(int program, int index, int size, IntBuffer type, IntBuffer name) {
      RenderSystem.assertOnRenderThread();
      return GL32C.glGetActiveUniform(program, index, size, type, name);
   }

   public static void readPixels(int x, int y, int width, int height, int format, int type, float[] pixels) {
      RenderSystem.assertOnRenderThread();
      GL32C.glReadPixels(x, y, width, height, format, type, pixels);
   }

   public static void bufferData(int target, float[] data, int usage) {
      RenderSystem.assertOnRenderThread();
      GL32C.glBufferData(target, data, usage);
   }

   public static int bufferStorage(int target, float[] data, int usage) {
      RenderSystem.assertOnRenderThread();
      return dsaState.bufferStorage(target, data, usage);
   }

   public static void bufferStorage(int target, long size, int flags) {
      RenderSystem.assertOnRenderThread();
      GL45C.glBufferStorage(target, size, flags);
   }

   public static void bindBufferBase(int target, Integer index, int buffer) {
      RenderSystem.assertOnRenderThread();
      GL43C.glBindBufferBase(target, index, buffer);
   }

   public static void vertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
      RenderSystem.assertOnRenderThread();
      GL32C.glVertexAttrib4f(index, v0, v1, v2, v3);
   }

   public static void detachShader(int program, int shader) {
      RenderSystem.assertOnRenderThread();
      GL32C.glDetachShader(program, shader);
   }

   public static void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
      dsaState.framebufferTexture2D(fb, fbtarget, attachment, target, texture, levels);
   }

   public static int getTexParameteri(int texture, int target, int pname) {
      RenderSystem.assertOnRenderThread();
      return dsaState.getTexParameteri(texture, target, pname);
   }

   public static void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
      RenderSystem.assertOnRenderThread();
      if (!GL.getCapabilities().OpenGL42 && !GL.getCapabilities().GL_ARB_shader_image_load_store) {
         EXTShaderImageLoadStore.glBindImageTextureEXT(unit, texture, level, layered, layer, access, format);
      } else {
         GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
      }
   }

   public static int getMaxImageUnits() {
      if (GL.getCapabilities().OpenGL42 || GL.getCapabilities().GL_ARB_shader_image_load_store) {
         return GlStateManager._getInteger(36664);
      } else {
         return GL.getCapabilities().GL_EXT_shader_image_load_store ? GlStateManager._getInteger(36664) : 0;
      }
   }

   public static boolean supportsSSBO() {
      return GL.getCapabilities().OpenGL44 || GL.getCapabilities().GL_ARB_shader_storage_buffer_object && GL.getCapabilities().GL_ARB_buffer_storage;
   }

   public static boolean supportsImageLoadStore() {
      return GL.getCapabilities().glBindImageTexture != 0L
         || GL.getCapabilities().OpenGL42
         || (GL.getCapabilities().GL_ARB_shader_image_load_store || GL.getCapabilities().GL_EXT_shader_image_load_store)
            && GL.getCapabilities().GL_ARB_buffer_storage;
   }

   public static void genBuffers(int[] buffers) {
      GL43C.glGenBuffers(buffers);
   }

   public static void clearBufferSubData(int glShaderStorageBuffer, int glR8, long offset, long size, int glRed, int glByte, int[] ints) {
      GL43C.glClearBufferSubData(glShaderStorageBuffer, glR8, offset, size, glRed, glByte, ints);
   }

   public static void getProgramiv(int program, int value, int[] storage) {
      GL32C.glGetProgramiv(program, value, storage);
   }

   public static void dispatchCompute(int workX, int workY, int workZ) {
      GL45C.glDispatchCompute(workX, workY, workZ);
   }

   public static void dispatchCompute(Vector3i workGroups) {
      GL45C.glDispatchCompute(workGroups.x, workGroups.y, workGroups.z);
   }

   public static void memoryBarrier(int barriers) {
      RenderSystem.assertOnRenderThread();
      if (supportsCompute) {
         GL45C.glMemoryBarrier(barriers);
      }
   }

   public static boolean supportsBufferBlending() {
      return GL.getCapabilities().GL_ARB_draw_buffers_blend || GL.getCapabilities().OpenGL40;
   }

   public static void disableBlend() {
      for (int i = 0; i < GlStateManagerAccessor.getBLEND_ENABLE().length; i++) {
         GlStateManager._disableBlend(i);
      }
   }

   public static void enableBlend() {
      for (int i = 0; i < GlStateManagerAccessor.getBLEND_ENABLE().length; i++) {
         GlStateManager._enableBlend(i);
      }
   }

   public static void disableBufferBlend(int buffer) {
      RenderSystem.assertOnRenderThread();
      GL32C.glDisablei(3042, buffer);
      GlStateManagerAccessor.getBLEND_ENABLE()[buffer] = false;
   }

   public static void enableBufferBlend(int buffer) {
      RenderSystem.assertOnRenderThread();
      GL32C.glEnablei(3042, buffer);
      GlStateManagerAccessor.getBLEND_ENABLE()[buffer] = true;
   }

   public static void blendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
      RenderSystem.assertOnRenderThread();
      ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
   }

   public static void bindTextureToUnit(int target, int unit, int texture) {
      dsaState.bindTextureToUnit(target, unit, texture);
   }

   public static int getUniformBlockIndex(int program, String uniformBlockName) {
      RenderSystem.assertOnRenderThread();
      return GL32C.glGetUniformBlockIndex(program, uniformBlockName);
   }

   public static void uniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
      RenderSystem.assertOnRenderThread();
      GL32C.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
   }

   public static void setShadowProjection(Matrix4f shadowProjection) {
      backupProjection = RenderSystem.getProjectionMatrixBuffer();
      backupProjectionType = RenderSystem.getProjectionType();
      RenderSystem.setProjectionMatrix(perspectiveProjectionMatrixBuffer.getBuffer(shadowProjection), ProjectionType.ORTHOGRAPHIC);
   }

   public static void restorePlayerProjection() {
      RenderSystem.setProjectionMatrix(backupProjection, backupProjectionType);
      backupProjection = null;
      backupProjectionType = null;
   }

   public static void blitFramebuffer(
      int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter
   ) {
      dsaState.blitFramebuffer(source, dest, offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
   }

   public static int createFramebuffer() {
      return dsaState.createFramebuffer();
   }

   public static int createTexture(int target) {
      return dsaState.createTexture(target);
   }

   public static void bindTextureForSetup(int glType, int glId) {
      if (glType == 3553) {
         lastTex = GlStateManagerAccessor.getTEXTURES()[GlStateManagerAccessor.getActiveTexture()].binding;
      }

      GL30C.glBindTexture(glType, glId);
   }

   public static void restoreTexture() {
      if (lastTex != -1) {
         GL30C.glBindTexture(3553, lastTex);
         lastTex = -1;
      }
   }

   public static boolean supportsCompute() {
      return supportsCompute;
   }

   public static boolean supportsTesselation() {
      return supportsTesselation;
   }

   public static int genSampler() {
      return GL33C.glGenSamplers();
   }

   public static void destroySampler(int glId) {
      GL33C.glDeleteSamplers(glId);
   }

   public static void bindSamplerToUnit(int unit, int sampler) {
      if (samplers[unit] != sampler) {
         GL33C.glBindSampler(unit, sampler);
         samplers[unit] = sampler;
      }
   }

   public static void unbindAllSamplers() {
      boolean usedASampler = false;

      for (int i = 0; i < samplers.length; i++) {
         if (samplers[i] != 0) {
            usedASampler = true;
            if (!hasMultibind) {
               GL33C.glBindSampler(i, 0);
            }

            samplers[i] = 0;
         }
      }

      if (usedASampler && hasMultibind) {
         GL45C.glBindSamplers(0, emptyArray);
      }
   }

   public static void samplerParameteri(int sampler, int pname, int param) {
      GL33C.glSamplerParameteri(sampler, pname, param);
   }

   public static void samplerParameterf(int sampler, int pname, float param) {
      GL33C.glSamplerParameterf(sampler, pname, param);
   }

   public static void samplerParameteriv(int sampler, int pname, int[] params) {
      GL33C.glSamplerParameteriv(sampler, pname, params);
   }

   public static long getVRAM() {
      return GL.getCapabilities().GL_NVX_gpu_memory_info ? GL32C.glGetInteger(36937) * 1024L : 4294967296L;
   }

   public static void deleteBuffers(int glId) {
      RenderSystem.assertOnRenderThread();
      GL43C.glDeleteBuffers(glId);
   }

   public static void setPolygonMode(int mode) {
      if (mode != polygonMode) {
         polygonMode = mode;
         GL43C.glPolygonMode(1032, mode);
      }
   }

   public static void overridePolygonMode() {
      backupPolygonMode = polygonMode;
      setPolygonMode(6914);
   }

   public static void restorePolygonMode() {
      setPolygonMode(backupPolygonMode);
      backupPolygonMode = 6914;
   }

   public static void dispatchComputeIndirect(long offset) {
      GL43C.glDispatchComputeIndirect(offset);
   }

   public static void bindBuffer(int target, int buffer) {
      GL46C.glBindBuffer(target, buffer);
   }

   public static int createBuffers() {
      return dsaState.createBuffers();
   }

   public static String getStringi(int glEnum, int index) {
      return GL46C.glGetStringi(glEnum, index);
   }

   public static void copyImageSubData(
      int sourceTexture,
      int target,
      int mip,
      int srcX,
      int srcY,
      int srcZ,
      int destTexture,
      int dstTarget,
      int dstMip,
      int dstX,
      int dstY,
      int dstZ,
      int width,
      int height,
      int depth
   ) {
      GL46C.glCopyImageSubData(sourceTexture, target, mip, srcX, srcY, srcZ, destTexture, dstTarget, dstMip, dstX, dstY, dstZ, width, height, depth);
   }

   public static void backupAndDisableCullingState(boolean b) {
      cullingState = Minecraft.getInstance().smartCull;
      Minecraft.getInstance().smartCull = Minecraft.getInstance().smartCull && !b;
   }

   public static void restoreCullingState() {
      Minecraft.getInstance().smartCull = cullingState;
      cullingState = true;
   }

   public static void onProgramUse() {
      for (int i = 0; i < textureToUnswizzle.size(); i++) {
         texParameteriv(textureToUnswizzle.getInt(i), TextureType.TEXTURE_2D.getGlType(), 36422, new int[]{6403, 6404, 6405, 6406});
      }

      textureToUnswizzle.clear();
   }

   public static void addUnswizzle(int shaderTexture) {
      textureToUnswizzle.add(shaderTexture);
   }

   public static int checkFramebufferStatus(int glFramebuffer) {
      return GL46C.glCheckFramebufferStatus(glFramebuffer);
   }

   public static void uniformMatrix3fv(int index, boolean b, FloatBuffer buf) {
      RenderSystem.assertOnRenderThread();
      GL46C.glUniformMatrix3fv(index, b, buf);
   }

   public static void uniformMatrix3fv(int index, boolean b, float[] buf) {
      RenderSystem.assertOnRenderThread();
      GL46C.glUniformMatrix3fv(index, b, buf);
   }

   public static void clearColor(float v, float v1, float v2, float v3) {
      RenderSystem.assertOnRenderThread();
      GL46C.glClearColor(v, v1, v2, v3);
   }

   public static int getAttribLocation(int handle, String irisNormal) {
      return GL46C.glGetAttribLocation(handle, irisNormal);
   }

   public static GlDevice getGlDevice() {
      return (GlDevice)((GpuDeviceAccessor)RenderSystem.getDevice()).getBackend();
   }

   public static class DSAARB extends IrisRenderSystem.DSAUnsupported {
      @Override
      public void generateMipmaps(int texture, int target) {
         ARBDirectStateAccess.glGenerateTextureMipmap(texture);
      }

      @Override
      public void texParameteri(int texture, int target, int pname, int param) {
         ARBDirectStateAccess.glTextureParameteri(texture, pname, param);
      }

      @Override
      public void texParameterf(int texture, int target, int pname, float param) {
         ARBDirectStateAccess.glTextureParameterf(texture, pname, param);
      }

      @Override
      public void texParameteriv(int texture, int target, int pname, int[] params) {
         ARBDirectStateAccess.glTextureParameteriv(texture, pname, params);
      }

      @Override
      public void readBuffer(int framebuffer, int buffer) {
         ARBDirectStateAccess.glNamedFramebufferReadBuffer(framebuffer, buffer);
      }

      @Override
      public void drawBuffers(int framebuffer, int[] buffers) {
         ARBDirectStateAccess.glNamedFramebufferDrawBuffers(framebuffer, buffers);
      }

      @Override
      public void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values) {
         ARBDirectStateAccess.glClearNamedFramebufferfv(framebuffer, buffer, drawbuffer, values);
      }

      @Override
      public void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
         ARBDirectStateAccess.glClearNamedFramebufferiv(framebuffer, buffer, drawbuffer, values);
      }

      @Override
      public void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
         ARBDirectStateAccess.glClearNamedFramebufferuiv(framebuffer, buffer, drawbuffer, values);
      }

      @Override
      public int getTexParameteri(int texture, int target, int pname) {
         return ARBDirectStateAccess.glGetTextureParameteri(texture, pname);
      }

      @Override
      public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
         ARBDirectStateAccess.glCopyTextureSubImage2D(destTexture, i, i1, i2, i3, i4, width, height);
      }

      @Override
      public void bindTextureToUnit(int target, int unit, int texture) {
         if (target == 3553) {
            if (GlStateManagerAccessor.getTEXTURES()[unit].binding == texture) {
               return;
            }

            ARBDirectStateAccess.glBindTextureUnit(unit, texture);
            GlStateManagerAccessor.getTEXTURES()[unit].binding = texture;
         } else {
            ARBDirectStateAccess.glBindTextureUnit(unit, texture);
         }
      }

      @Override
      public int bufferStorage(int target, float[] data, int usage) {
         int buffer = GL45C.glCreateBuffers();
         GL45C.glNamedBufferData(buffer, data, usage);
         return buffer;
      }

      @Override
      public int createBuffers() {
         return ARBDirectStateAccess.glCreateBuffers();
      }

      @Override
      public void blitFramebuffer(
         int source,
         int dest,
         int offsetX,
         int offsetY,
         int width,
         int height,
         int offsetX2,
         int offsetY2,
         int width2,
         int height2,
         int bufferChoice,
         int filter
      ) {
         ARBDirectStateAccess.glBlitNamedFramebuffer(source, dest, offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
      }

      @Override
      public void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
         ARBDirectStateAccess.glNamedFramebufferTexture(fb, attachment, texture, levels);
      }

      @Override
      public int createFramebuffer() {
         return ARBDirectStateAccess.glCreateFramebuffers();
      }

      @Override
      public int createTexture(int target) {
         return ARBDirectStateAccess.glCreateTextures(target);
      }
   }

   public interface DSAAccess {
      void generateMipmaps(int var1, int var2);

      void texParameteri(int var1, int var2, int var3, int var4);

      void texParameterf(int var1, int var2, int var3, float var4);

      void texParameteriv(int var1, int var2, int var3, int[] var4);

      void readBuffer(int var1, int var2);

      void drawBuffers(int var1, int[] var2);

      void clearBufferfv(int var1, int var2, int var3, float[] var4);

      void clearBufferiv(int var1, int var2, int var3, int[] var4);

      void clearBufferuiv(int var1, int var2, int var3, int[] var4);

      int getTexParameteri(int var1, int var2, int var3);

      void copyTexSubImage2D(int var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9);

      void bindTextureToUnit(int var1, int var2, int var3);

      int bufferStorage(int var1, float[] var2, int var3);

      void blitFramebuffer(int var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9, int var10, int var11, int var12);

      void framebufferTexture2D(int var1, int var2, int var3, int var4, int var5, int var6);

      int createFramebuffer();

      int createTexture(int var1);

      int createBuffers();
   }

   public static class DSACore extends IrisRenderSystem.DSAARB {
   }

   public static class DSAUnsupported implements IrisRenderSystem.DSAAccess {
      @Override
      public void generateMipmaps(int texture, int target) {
         int previous = GlStateManagerAccessor.getTEXTURES()[GlStateManagerAccessor.getActiveTexture()].binding;
         GlStateManager._bindTexture(texture);
         GL32C.glGenerateMipmap(target);
         GlStateManager._bindTexture(previous);
      }

      @Override
      public void texParameteri(int texture, int target, int pname, int param) {
         IrisRenderSystem.bindTextureForSetup(target, texture);
         GL32C.glTexParameteri(target, pname, param);
         IrisRenderSystem.restoreTexture();
      }

      @Override
      public void texParameterf(int texture, int target, int pname, float param) {
         IrisRenderSystem.bindTextureForSetup(target, texture);
         GL32C.glTexParameterf(target, pname, param);
         IrisRenderSystem.restoreTexture();
      }

      @Override
      public void texParameteriv(int texture, int target, int pname, int[] params) {
         IrisRenderSystem.bindTextureForSetup(target, texture);
         GL32C.glTexParameteriv(target, pname, params);
         IrisRenderSystem.restoreTexture();
      }

      @Override
      public void readBuffer(int framebuffer, int buffer) {
         GlStateManager._glBindFramebuffer(36160, framebuffer);
         GL32C.glReadBuffer(buffer);
      }

      @Override
      public void drawBuffers(int framebuffer, int[] buffers) {
         GlStateManager._glBindFramebuffer(36160, framebuffer);
         GL32C.glDrawBuffers(buffers);
      }

      @Override
      public void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values) {
         GlStateManager._glBindFramebuffer(36160, framebuffer);
         GL32C.glClearBufferfv(buffer, drawbuffer, values);
      }

      @Override
      public void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
         GlStateManager._glBindFramebuffer(36160, framebuffer);
         GL32C.glClearBufferiv(buffer, drawbuffer, values);
      }

      @Override
      public void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
         GlStateManager._glBindFramebuffer(36160, framebuffer);
         GL32C.glClearBufferuiv(buffer, drawbuffer, values);
      }

      @Override
      public int getTexParameteri(int texture, int target, int pname) {
         IrisRenderSystem.bindTextureForSetup(target, texture);
         return GL32C.glGetTexParameteri(target, pname);
      }

      @Override
      public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
         int previous = GlStateManagerAccessor.getTEXTURES()[GlStateManagerAccessor.getActiveTexture()].binding;
         GlStateManager._bindTexture(destTexture);
         GL32C.glCopyTexSubImage2D(target, i, i1, i2, i3, i4, width, height);
         GlStateManager._bindTexture(previous);
      }

      @Override
      public void bindTextureToUnit(int target, int unit, int texture) {
         int activeTexture = GlStateManagerAccessor.getActiveTexture();
         GlStateManager._activeTexture(33984 + unit);
         GL46C.glBindTexture(target, texture);
         if (target == 3553) {
            GlStateManagerAccessor.getTEXTURES()[unit].binding = texture;
         }

         GlStateManager._activeTexture(33984 + activeTexture);
      }

      @Override
      public int bufferStorage(int target, float[] data, int usage) {
         int buffer = GlStateManager._glGenBuffers();
         GlStateManager._glBindBuffer(target, buffer);
         IrisRenderSystem.bufferData(target, data, usage);
         GlStateManager._glBindBuffer(target, 0);
         return buffer;
      }

      @Override
      public void blitFramebuffer(
         int source,
         int dest,
         int offsetX,
         int offsetY,
         int width,
         int height,
         int offsetX2,
         int offsetY2,
         int width2,
         int height2,
         int bufferChoice,
         int filter
      ) {
         GlStateManager._glBindFramebuffer(36008, source);
         GlStateManager._glBindFramebuffer(36009, dest);
         GL32C.glBlitFramebuffer(offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
      }

      @Override
      public void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
         GlStateManager._glBindFramebuffer(fbtarget, fb);
         GL32C.glFramebufferTexture2D(fbtarget, attachment, target, texture, levels);
      }

      @Override
      public int createFramebuffer() {
         int framebuffer = GlStateManager.glGenFramebuffers();
         GlStateManager._glBindFramebuffer(36160, framebuffer);
         return framebuffer;
      }

      @Override
      public int createTexture(int target) {
         int texture = GlStateManager._genTexture();
         IrisRenderSystem.bindTextureForSetup(target, texture);
         IrisRenderSystem.restoreTexture();
         return texture;
      }

      @Override
      public int createBuffers() {
         return GlStateManager._glGenBuffers();
      }
   }
}
