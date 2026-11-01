package net.irisshaders.iris.gl.program;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;

public final class Program extends GlResource {
   private final ProgramUniforms uniforms;
   private final ProgramSamplers samplers;
   private final ProgramImages images;

   Program(int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
      super(program);
      this.uniforms = uniforms;
      this.samplers = samplers;
      this.images = images;
   }

   public static void unbind() {
      ProgramUniforms.clearActiveUniforms();
      ProgramSamplers.clearActiveSamplers();
      GlStateManager._glUseProgram(0);
   }

   public void use() {
      IrisRenderSystem.memoryBarrier(8232);
      GlStateManager._glUseProgram(this.getGlId());
      this.uniforms.update();
      this.samplers.update();
      this.images.update();
   }

   @Override
   public void destroyInternal() {
      GlStateManager.glDeleteProgram(this.getGlId());
   }

   @Deprecated
   public int getProgramId() {
      return this.getGlId();
   }

   public int getActiveImages() {
      return this.images.getActiveImages();
   }
}
