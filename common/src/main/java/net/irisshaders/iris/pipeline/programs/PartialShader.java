package net.irisshaders.iris.pipeline.programs;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.Objects;
import net.irisshaders.iris.gl.IrisRenderSystem;

public final class PartialShader {
   private final int program;
   private final int vertexS;
   private final int fragS;
   private final int geometryS;
   private final int tessContS;
   private final int tessEvalS;
   private boolean hasUnbound = false;

   public PartialShader(int program, int vertexS, int fragS, int geometryS, int tessContS, int tessEvalS) {
      this.program = program;
      this.vertexS = vertexS;
      this.fragS = fragS;
      this.geometryS = geometryS;
      this.tessContS = tessContS;
      this.tessEvalS = tessEvalS;
   }

   public int getFinally() {
      if (!this.hasUnbound) {
         this.hasUnbound = true;
         IrisBindings.apply(this.program);
         detachIfValid(this.program, this.vertexS);
         detachIfValid(this.program, this.fragS);
         detachIfValid(this.program, this.geometryS);
         detachIfValid(this.program, this.tessContS);
         detachIfValid(this.program, this.tessEvalS);
      }

      return this.program;
   }

   private static void detachIfValid(int i, int s) {
      if (s >= 0) {
         IrisRenderSystem.detachShader(i, s);
         GlStateManager.glDeleteShader(s);
      }
   }

   public int program() {
      return this.program;
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == this) {
         return true;
      } else if (obj != null && obj.getClass() == this.getClass()) {
         PartialShader that = (PartialShader)obj;
         return this.program == that.program
            && this.vertexS == that.vertexS
            && this.fragS == that.fragS
            && this.geometryS == that.geometryS
            && this.tessContS == that.tessContS
            && this.tessEvalS == that.tessEvalS;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.program, this.vertexS, this.fragS, this.geometryS, this.tessContS, this.tessEvalS);
   }

   @Override
   public String toString() {
      return "PartialShader[program="
         + this.program
         + ", vertexS="
         + this.vertexS
         + ", fragS="
         + this.fragS
         + ", geometryS="
         + this.geometryS
         + ", tessContS="
         + this.tessContS
         + ", tessEvalS="
         + this.tessEvalS
         + "]";
   }
}
