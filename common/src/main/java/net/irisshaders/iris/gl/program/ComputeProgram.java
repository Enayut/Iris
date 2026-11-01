package net.irisshaders.iris.gl.program;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.FilledIndirectPointer;
import org.joml.Vector2f;
import org.joml.Vector3i;

public final class ComputeProgram extends GlResource {
   private final ProgramUniforms uniforms;
   private final ProgramSamplers samplers;
   private final ProgramImages images;
   private final int[] localSize = new int[3];
   private Vector3i absoluteWorkGroups;
   private Vector2f relativeWorkGroups;
   private float cachedWidth;
   private float cachedHeight;
   private Vector3i cachedWorkGroups;
   private FilledIndirectPointer indirectPointer;

   ComputeProgram(int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
      super(program);
      IrisRenderSystem.getProgramiv(program, 33383, this.localSize);
      this.uniforms = uniforms;
      this.samplers = samplers;
      this.images = images;
   }

   public static void unbind() {
      ProgramUniforms.clearActiveUniforms();
      GlStateManager._glUseProgram(0);
   }

   public void setWorkGroupInfo(Vector2f relativeWorkGroups, Vector3i absoluteWorkGroups, FilledIndirectPointer indirectPointer) {
      this.relativeWorkGroups = relativeWorkGroups;
      this.absoluteWorkGroups = absoluteWorkGroups;
      this.indirectPointer = indirectPointer;
   }

   public Vector3i getWorkGroups(float width, float height) {
      if (this.indirectPointer != null) {
         return null;
      }

      if (this.cachedWidth != width || this.cachedHeight != height || this.cachedWorkGroups == null) {
         this.cachedWidth = width;
         this.cachedHeight = height;
         if (this.absoluteWorkGroups != null) {
            this.cachedWorkGroups = this.absoluteWorkGroups;
         } else if (this.relativeWorkGroups != null) {
            this.cachedWorkGroups = new Vector3i(
               (int)Math.ceil(Math.ceil(width * this.relativeWorkGroups.x) / this.localSize[0]),
               (int)Math.ceil(Math.ceil(height * this.relativeWorkGroups.y) / this.localSize[1]),
               1
            );
         } else {
            this.cachedWorkGroups = new Vector3i((int)Math.ceil(width / this.localSize[0]), (int)Math.ceil(height / this.localSize[1]), 1);
         }
      }

      return this.cachedWorkGroups;
   }

   public void use() {
      GlStateManager._glUseProgram(this.getGlId());
      this.uniforms.update();
      this.samplers.update();
      this.images.update();
   }

   public void dispatch(float width, float height) {
      int barriers = 0;
      if (!Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::allowConcurrentCompute).orElse(false)) {
         barriers |= 8232;
      }

      if (this.indirectPointer != null) {
         barriers |= 64;
      }

      if (barriers != 0) {
         IrisRenderSystem.memoryBarrier(barriers);
      }

      if (this.indirectPointer != null) {
         IrisRenderSystem.bindBuffer(37102, this.indirectPointer.buffer());
         IrisRenderSystem.dispatchComputeIndirect(this.indirectPointer.offset());
      } else {
         IrisRenderSystem.dispatchCompute(this.getWorkGroups(width, height));
      }
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
