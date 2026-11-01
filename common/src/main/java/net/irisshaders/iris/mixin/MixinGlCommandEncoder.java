package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.renderpearl.backend.opengl.GlCommandEncoder;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.backend.opengl.GlRenderPass;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.ArrayList;
import java.util.List;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.DepthColorStorage;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.pipeline.programs.IrisProgram;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL33C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlCommandEncoder.class)
public class MixinGlCommandEncoder {
   @Shadow
   @Nullable
   private GlRenderPipeline lastPipeline;
   @Shadow
   @Nullable
   private GlProgram lastProgram;
   @Unique
   private int tempFBO;
   @Unique
   private List<IrisProgram> programsToClear = new ArrayList<>();
   @Unique
   private static GlRenderPass lastPass;

   @Redirect(method = "createRenderPass", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_viewport(IIII)V"))
   private void changeViewport(int i, int j, int k, int l) {
      if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
         GlStateManager._viewport(i, j, k, l);
      }
   }

   @Redirect(method = "createRenderPass", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_scissorBox(IIII)V"))
   private void changeViewport2(int i, int j, int k, int l) {
      if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
         GlStateManager._scissorBox(0, 0, ShadowRenderer.RESOLUTION, ShadowRenderer.RESOLUTION);
      } else {
         GlStateManager._scissorBox(i, j, k, l);
      }
   }

   @Redirect(method = "createRenderPass", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/ScissorState;enable(IIII)V"))
   private void changeViewport3(ScissorState instance, int x, int y, int width, int height) {
      if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
         instance.enable(0, 0, ShadowRenderer.RESOLUTION, ShadowRenderer.RESOLUTION);
      } else {
         instance.enable(x, y, width, height);
      }
   }

   @Redirect(method = "createRenderPass", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_glBindFramebuffer(II)V"))
   private void changeFramebuffer(int i, int j) {
      if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered() && !ImmediateState.safeToMultiply) {
         GlStateManager._glBindFramebuffer(i, j);
      } else {
         this.tempFBO = j;
      }
   }

   @Redirect(method = "createRenderPass", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL33C;glDrawBuffers([I)V"))
   private void iris$changeDrawBuffers(int[] buffers) {
      if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered() && !ImmediateState.safeToMultiply) {
         GL33C.glDrawBuffers(buffers);
      }
   }

   @Inject(
      method = "setupDraw",
      at = @At(
         value = "FIELD",
         target = "Lcom/mojang/renderpearl/backend/opengl/GlCommandEncoder;lastPipeline:Lcom/mojang/renderpearl/backend/opengl/GlRenderPipeline;",
         opcode = 180
      ),
      cancellable = true
   )
   private void iris$bypassSetup(GlRenderPass glRenderPass, CallbackInfo cir) {
      DepthColorStorage.unlockDepthColor();
      if (ImmediateState.safeToMultiply && !(glRenderPass.pipeline.program() instanceof ExtendedShader)) {
         GlStateManager._glBindFramebuffer(36160, this.tempFBO);
      }

      lastPass = glRenderPass;
      if (Iris.isPackInUseQuick() || glRenderPass.iris$getCustomPass() != null) {
         this.lastProgram = null;
      }

      if (glRenderPass.iris$getCustomPass() != null) {
         cir.cancel();
         BlendModeOverride.restore();
         IrisRenderSystem.disableBlend();
         glRenderPass.iris$getCustomPass().setupState();
         if (glRenderPass.isScissorEnabled()) {
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(glRenderPass.getScissorX(), glRenderPass.getScissorY(), glRenderPass.getScissorWidth(), glRenderPass.getScissorHeight());
         } else {
            GlStateManager._disableScissorTest();
         }

         GlStateManager._disableDepthTest();
         GlStateManager._depthMask(false);
         GlStateManager._disablePolygonOffset();
         GlStateManager._disableCull();
         GlStateManager._colorMask(15);
      }

      if (glRenderPass.pipeline.program() instanceof ExtendedShader shader) {
         ImmediateState.usingTessellation = shader.usesTessellation();
      }
   }

   @Inject(method = "setupDraw", at = @At("RETURN"))
   private void iris$trackProgram(GlRenderPass glRenderPass, CallbackInfo ci) {
      if (glRenderPass.pipeline.program() instanceof IrisProgram irisProgram && !this.programsToClear.contains(irisProgram)) {
         this.programsToClear.add(irisProgram);
      }
   }

   @Inject(method = "submitRenderPass", at = @At("HEAD"))
   private void iris$clearState(CallbackInfo ci) {
      this.programsToClear.forEach(IrisProgram::iris$clearState);
      this.programsToClear.clear();
   }

   @ModifyArg(method = "executeDraws", index = 0, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL33C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"))
   private int iris$terrainTessShaderCompat(int mode) {
      if (mode == 4 && ImmediateState.usingTessellation) {
         mode = 14;
      }

      return mode;
   }

   @ModifyArg(method = "executeDraw", index = 0, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL33C;glDrawElementsInstancedBaseVertex(IIIJII)V"))
   private int iris$entityTessShaderCompat(int mode) {
      if (mode == 4 && ImmediateState.usingTessellation) {
         mode = 14;
      }

      return mode;
   }
}
