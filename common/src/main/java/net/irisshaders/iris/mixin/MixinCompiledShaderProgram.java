package net.irisshaders.iris.mixin;

import com.google.common.collect.ImmutableSet;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import java.lang.invoke.MethodHandle;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.SkipList;
import net.irisshaders.iris.gl.blending.DepthColorStorage;
import net.irisshaders.iris.mixinterface.ShaderInstanceInterface;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.ShaderRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.pipeline.programs.FallbackShader;
import net.irisshaders.iris.pipeline.programs.IrisProgram;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL31C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlProgram.class)
public abstract class MixinCompiledShaderProgram implements ShaderInstanceInterface {
   @Unique
   private static final ImmutableSet<String> ATTRIBUTE_LIST = ImmutableSet.of("Position", "Color", "Normal", "UV0", "UV1", "UV2", new String[0]);
   @Unique
   private static GlProgram lastAppliedShader;
   @Unique
   private MethodHandle shouldSkip;

   @Override
   public void setShouldSkip(MethodHandle s) {
      this.shouldSkip = s;
   }

   public boolean iris$shouldSkipThis() {
      if (Iris.getIrisConfig().shouldAllowUnknownShaders()) {
         if (ShadowRenderer.ACTIVE) {
            return true;
         }

         if (!shouldOverrideShaders()) {
            return false;
         }

         if (this.shouldSkip == SkipList.NONE) {
            return false;
         }

         if (this.shouldSkip == SkipList.ALWAYS) {
            return true;
         }

         try {
            return (boolean)this.shouldSkip.invoke((GlProgram)(Object)this);
         } catch (Throwable e) {
            throw new RuntimeException(e);
         }
      } else {
         return !(((Object) this) instanceof ExtendedShader) && !(((Object) this) instanceof FallbackShader) && shouldOverrideShaders();
      }
   }

   @Unique
   private static boolean shouldOverrideShaders() {
      WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
      return pipeline instanceof ShaderRenderingPipeline ? ((ShaderRenderingPipeline)pipeline).shouldOverrideShaders() : false;
   }

   @Redirect(
      method = "setupBindGroupLayouts",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL33C;glGetUniformBlockIndex(ILjava/lang/CharSequence;)I")
   )
   private int iris$changeIndex(int program, CharSequence uniformBlockName) {
      return this instanceof IrisProgram is ? is.iris$getBlockIndex(program, uniformBlockName) : GL31C.glGetUniformBlockIndex(program, uniformBlockName);
   }

   private void onTail(CallbackInfo ci) {
      if (!this.iris$shouldSkipThis()) {
         if (!this.isKnownShader() && shouldOverrideShaders()) {
            WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
            if (pipeline instanceof IrisRenderingPipeline && !ShadowRenderer.ACTIVE) {
               ((IrisRenderingPipeline)pipeline).bindDefault();
            }
         }
      } else {
         if (ImmediateState.isRenderingLevel && !this.isKnownShader()) {
            DepthColorStorage.disableDepthColor();
         } else {
            DepthColorStorage.unlockDepthColor();
         }
      }
   }

   private boolean isKnownShader() {
      return ((Object) this) instanceof ExtendedShader || ((Object) this) instanceof FallbackShader;
   }

   private void iris$unlockDepthColorState(CallbackInfo ci) {
      if (!this.iris$shouldSkipThis()) {
         if (!this.isKnownShader() && shouldOverrideShaders()) {
            WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
            if (pipeline instanceof IrisRenderingPipeline) {
               Minecraft.getInstance().gameRenderer.mainRenderTarget().iris$bindFramebuffer();
            }
         }
      } else {
         DepthColorStorage.unlockDepthColor();
      }
   }

   static {
      SkipList.shouldSkipList.put(ExtendedShader.class, SkipList.NONE);
      SkipList.shouldSkipList.put(FallbackShader.class, SkipList.NONE);
   }
}
