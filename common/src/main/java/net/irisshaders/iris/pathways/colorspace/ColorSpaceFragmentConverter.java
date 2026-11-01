package net.irisshaders.iris.pathways.colorspace;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.mixinterface.CustomPass;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.minecraft.client.Minecraft;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;

public class ColorSpaceFragmentConverter implements ColorSpaceConverter {
   private static final CustomPass EMPTY = new CustomPass() {
      @Override
      public void setupState() {
      }
   };
   private int width;
   private int height;
   private ColorSpace colorSpace;
   private Program program;
   private GlFramebuffer framebuffer;
   private int swapTexture;
   private GlTexture target;

   public ColorSpaceFragmentConverter(int width, int height, ColorSpace colorSpace) {
      this.rebuildProgram(width, height, colorSpace);
   }

   @Override
   public void rebuildProgram(int width, int height, ColorSpace colorSpace) {
      if (this.program != null) {
         this.program.destroy();
         this.program = null;
         this.framebuffer.destroy();
         this.framebuffer = null;
         GlStateManager._deleteTexture(this.swapTexture);
         this.swapTexture = 0;
      }

      this.width = width;
      this.height = height;
      this.colorSpace = colorSpace;

      String vertexSource;
      String source;
      try {
         vertexSource = new String(IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResourceAsStream("/colorSpace.vsh"))), StandardCharsets.UTF_8);
         source = new String(IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResourceAsStream("/colorSpace.csh"))), StandardCharsets.UTF_8);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }

      List<StringPair> defineList = new ArrayList<>();
      defineList.add(new StringPair("CURRENT_COLOR_SPACE", String.valueOf(colorSpace.ordinal())));

      for (ColorSpace space : ColorSpace.values()) {
         defineList.add(new StringPair(space.name(), String.valueOf(space.ordinal())));
      }

      source = JcppProcessor.glslPreprocessSource(source, defineList);
      ProgramBuilder builder = ProgramBuilder.begin("colorSpaceFragment", vertexSource, null, source, ImmutableSet.of());
      builder.uniformMatrix(
         UniformUpdateFrequency.ONCE,
         "projection",
         () -> new Matrix4f(2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, -1.0F, 0.0F, 1.0F)
      );
      builder.addDynamicSampler(() -> this.target.glId(), GlSampler.NEAREST, "readImage");
      this.swapTexture = GlStateManager._genTexture();
      IrisRenderSystem.texImage2D(this.swapTexture, 3553, 0, 32856, width, height, 0, 6408, 5121, null);
      this.framebuffer = new GlFramebuffer();
      this.framebuffer.addColorAttachment(0, this.swapTexture);
      this.program = builder.build();
   }

   @Override
   public void process(GlTexture targetImage) {
      if (this.colorSpace != ColorSpace.SRGB) {
         this.target = targetImage;
         GpuBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(6);
         IndexType type = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).type();
         RenderPass pass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(() -> "Color space", Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(), Optional.empty());

         try {
            pass.setPipeline(RenderSystem.getCompiledPipeline(CompositeRenderer.COMPOSITE_PIPELINE));
            pass.iris$setCustomPass(EMPTY);
            this.program.use();
            this.framebuffer.bind();
            pass.setIndexBuffer(indices, type);
            pass.setVertexBuffer(0, FullScreenQuadRenderer.INSTANCE.getQuad().slice());
            pass.drawIndexed(6, 1, 0, 0, 0);
         } catch (Throwable var8) {
            if (pass != null) {
               try {
                  pass.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }
            }

            throw var8;
         }

         if (pass != null) {
            pass.close();
         }

         Program.unbind();
         this.framebuffer.bindAsReadBuffer();
         IrisRenderSystem.copyTexSubImage2D(targetImage.glId(), 3553, 0, 0, 0, 0, 0, this.width, this.height);
      }
   }
}
