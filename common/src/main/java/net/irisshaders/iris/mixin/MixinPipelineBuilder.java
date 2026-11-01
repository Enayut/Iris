package net.irisshaders.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline.Pending;
import com.mojang.renderpearl.frontend.shaders.PipelineBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.irisshaders.iris.vertices.ImmediateState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PipelineBuilder.class)
public class MixinPipelineBuilder {
   @WrapMethod(method = "compilePipeline")
   private CompletableFuture<Pending> iris$useDeclaredVertexFormats(
      RenderPipeline pipeline, ShaderSource shaderSource, Executor executor, Operation<CompletableFuture<Pending>> original
   ) {
      boolean previous = ImmediateState.skipExtension.get();
      ImmediateState.skipExtension.set(true);

      try {
         return (CompletableFuture<Pending>)original.call(new Object[]{pipeline, shaderSource, executor});
      } finally {
         ImmediateState.skipExtension.set(previous);
      }
   }
}
