package net.irisshaders.iris.pathways;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.texture.DepthCopyStrategy;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.PixelType;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.mixinterface.CustomPass;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;

public class CenterDepthSampler {
   private static final double LN2 = Math.log(2.0);
   private static final CustomPass EMPTY_STATE = new CustomPass() {
      @Override
      public void setupState() {
      }
   };
   private final Program program;
   private final GlFramebuffer framebuffer;
   private final int texture = GlStateManager._genTexture();
   private final int altTexture = GlStateManager._genTexture();
   private boolean hasFirstSample;
   private boolean everRetrieved;
   private boolean destroyed;

   public CenterDepthSampler(IntSupplier depthSupplier, float halfLife) {
      this.framebuffer = new GlFramebuffer();
      InternalTextureFormat format = InternalTextureFormat.R32F;
      this.setupColorTexture(this.texture, format);
      this.setupColorTexture(this.altTexture, format);
      GlStateManager._bindTexture(0);
      this.framebuffer.addColorAttachment(0, this.texture);

      ProgramBuilder builder;
      try {
         String fsh = new String(IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResourceAsStream("/centerDepth.fsh"))), StandardCharsets.UTF_8);
         String vsh = new String(IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResourceAsStream("/centerDepth.vsh"))), StandardCharsets.UTF_8);
         builder = ProgramBuilder.begin("centerDepthSmooth", vsh, null, fsh, ImmutableSet.of(0, 1, 2));
      } catch (IOException e) {
         throw new RuntimeException(e);
      }

      builder.addDynamicSampler(depthSupplier, GlSampler.NEAREST, "depth");
      builder.addDynamicSampler(() -> this.altTexture, GlSampler.NEAREST, "altDepth");
      builder.uniform1f(UniformUpdateFrequency.PER_FRAME, "lastFrameTime", SystemTimeUniforms.TIMER::getLastFrameTime);
      builder.uniform1f(UniformUpdateFrequency.ONCE, "decay", () -> 1.0 / (halfLife * 0.1 / LN2));
      builder.uniformMatrix(
         UniformUpdateFrequency.ONCE,
         "projection",
         () -> new Matrix4f(2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, -1.0F, 0.0F, 1.0F)
      );
      this.program = builder.build();
   }

   public void sampleCenterDepth() {
      if ((!this.hasFirstSample || this.everRetrieved) && !this.destroyed) {
         this.hasFirstSample = true;
         GpuBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(6);
         IndexType type = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).type();
         BlendModeOverride.restore();
         IrisRenderSystem.disableBlend();
         RenderPass renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
               () -> "centerDepthSmooth sampler", Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(), Optional.empty()
            );

         try {
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(CompositeRenderer.COMPOSITE_PIPELINE));
            renderPass.setIndexBuffer(indices, type);
            renderPass.setVertexBuffer(0, FullScreenQuadRenderer.INSTANCE.getQuad().slice());
            renderPass.iris$setCustomPass(EMPTY_STATE);
            this.framebuffer.bind();
            this.program.use();
            GlStateManager._viewport(0, 0, 1, 1);
            renderPass.drawIndexed(6, 1, 0, 0, 0);
            ProgramUniforms.clearActiveUniforms();
            ProgramSamplers.clearActiveSamplers();
            BlendModeOverride.restore();
         } catch (Throwable var7) {
            if (renderPass != null) {
               try {
                  renderPass.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }

         if (renderPass != null) {
            renderPass.close();
         }

         DepthCopyStrategy.fastest(false).copy(this.framebuffer, this.texture, null, this.altTexture, 1, 1);
      }
   }

   public void setupColorTexture(int texture, InternalTextureFormat format) {
      IrisRenderSystem.texImage2D(texture, 3553, 0, format.getGlFormat(), 1, 1, 0, format.getPixelFormat().getGlFormat(), PixelType.FLOAT.getGlFormat(), null);
      IrisRenderSystem.texParameteri(texture, 3553, 10241, 9729);
      IrisRenderSystem.texParameteri(texture, 3553, 10240, 9729);
      IrisRenderSystem.texParameteri(texture, 3553, 10242, 33071);
      IrisRenderSystem.texParameteri(texture, 3553, 10243, 33071);
   }

   public int getCenterDepthTexture() {
      return this.altTexture;
   }

   public void setUsage(boolean usage) {
      this.everRetrieved |= usage;
   }

   public void destroy() {
      GlStateManager._deleteTexture(this.texture);
      GlStateManager._deleteTexture(this.altTexture);
      this.framebuffer.destroy();
      this.program.destroy();
      this.destroyed = true;
   }
}
