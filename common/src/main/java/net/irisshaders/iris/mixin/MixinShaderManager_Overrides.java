package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline.CreateInfo;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline.CreateInfo.AttribBinding;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline.CreateInfo.VertexBuffer;
import com.mojang.renderpearl.backend.opengl.GlDevice;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import com.mojang.renderpearl.backend.opengl.VertexArray;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.mixinterface.GlRenderPipelineAccess;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.renderer.RenderPipelines;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSystem.class)
public abstract class MixinShaderManager_Overrides {
   @Unique
   private static Set<RenderPipeline> missingShaders = new HashSet<>();
   @Unique
   private static final Map<CompiledRenderPipeline, Map<GlProgram, Map<List<VertexFormat>, FrontendRenderPipeline>>> iris$overrides = new IdentityHashMap<>();

   @Inject(method = "getCompiledPipelineNullable", at = @At("RETURN"), cancellable = true)
   private static void redirectIrisProgram(RenderPipeline renderPipeline, CallbackInfoReturnable<CompiledRenderPipeline> cir) {
      if (renderPipeline != CompositeRenderer.COMPOSITE_PIPELINE) {
         if (renderPipeline != RenderPipelines.ANIMATE_SPRITE_BLIT && renderPipeline != RenderPipelines.ANIMATE_SPRITE_INTERPOLATE) {
            if (Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline irisPipeline
               && irisPipeline.shouldOverrideShaders()
               && !ImmediateState.bypass) {
               RenderPipeline newProgram = renderPipeline;
               ShaderKey shaderKey = IrisPipelines.getPipeline(irisPipeline, newProgram);
               GlProgram program = shaderKey == null ? null : irisPipeline.getShaderMap().getShader(shaderKey);
               GlRenderPipeline oldProgram = (GlRenderPipeline)((FrontendRenderPipeline)cir.getReturnValue()).backendRenderPipeline();
               FrontendRenderPipeline old2 = (FrontendRenderPipeline)cir.getReturnValue();
               if (program != null) {
                  GlDevice device = (GlDevice)((GpuDeviceAccessor)RenderSystem.getDevice()).getBackend();
                  List<VertexFormat> vertexFormats = new ArrayList<>(renderPipeline.getVertexFormatBindings());
                  FrontendRenderPipeline cached = iris$overrides.computeIfAbsent(old2, unused -> new IdentityHashMap<>())
                     .computeIfAbsent(program, unused -> new HashMap<>())
                     .get(vertexFormats);
                  if (cached != null) {
                     cir.setReturnValue(cached);
                     return;
                  }

                  CreateInfo createInfo = iris$createInfo(((GlRenderPipelineAccess)oldProgram).getCreateInfo(), program, vertexFormats);
                  VertexArray vertexArray = (VertexArray)VertexArray.createSource(GL.getCapabilities(), new HashSet()).apply(program, createInfo);
                  FrontendRenderPipeline replacement = new FrontendRenderPipeline(
                     old2.name(),
                     new GlRenderPipeline(device, createInfo, program, vertexArray),
                     vertexFormats,
                     old2.uniformIndices(),
                     old2.uniforms(),
                     old2.colorTargetStates(),
                     old2.wantsDepthTexture(),
                     old2.pushConstantSize()
                  );
                  iris$overrides.get(old2).get(program).put(vertexFormats, replacement);
                  cir.setReturnValue(replacement);
               } else if (missingShaders.add(renderPipeline)) {
                  if (renderPipeline.getLocation().getNamespace().equals("minecraft")) {
                     Iris.logger.fatal("Missing program " + renderPipeline.getLocation() + " in override list. This is likely an Iris bug!!!", new Throwable());
                  } else {
                     Iris.logger
                        .error(
                           "Missing program "
                              + renderPipeline.getLocation()
                              + " in override list. This is not a critical problem, but it could lead to weird rendering.",
                           new Throwable()
                        );
                  }
               }
            }
         }
      }
   }

   @Unique
   private static CreateInfo iris$createInfo(CreateInfo original, GlProgram program, List<VertexFormat> vertexFormats) {
      List<VertexBuffer> vertexBuffers = new ArrayList<>();
      List<AttribBinding> attribBindings = new ArrayList<>();

      for (int slot = 0; slot < vertexFormats.size(); slot++) {
         VertexFormat format = vertexFormats.get(slot);
         if (format != null) {
            vertexBuffers.add(new VertexBuffer(slot, format.getVertexSize(), format.getStepRate()));

            for (VertexFormatElement element : format.getElements()) {
               int location = GL33C.glGetAttribLocation(program.getProgramId(), "iris_" + element.name());
               if (location == -1) {
                  location = GL33C.glGetAttribLocation(program.getProgramId(), element.name());
               }

               if (location != -1) {
                  attribBindings.add(new AttribBinding(slot, location, element.offset(), element.format()));
               }
            }
         }
      }

      return new CreateInfo(
         original.name(),
         original.shaders(),
         vertexBuffers,
         attribBindings,
         original.uniforms(),
         original.pushConstantsSize(),
         original.depthStencilState(),
         original.polygonMode(),
         original.cull(),
         original.colorTargetStates(),
         original.primitiveTopology()
      );
   }
}
