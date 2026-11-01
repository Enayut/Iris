package net.irisshaders.iris.pathways;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import net.irisshaders.iris.pipeline.CompositeRenderer;

public class FullScreenQuadRenderer {
   public static final FullScreenQuadRenderer INSTANCE = new FullScreenQuadRenderer();
   private final GpuBuffer quad;

   private FullScreenQuadRenderer() {
      ByteBufferBuilder x = new ByteBufferBuilder(64);
      BufferBuilder bufferBuilder = new BufferBuilder(x, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
      bufferBuilder.addVertex(0.0F, 0.0F, 0.0F).setUv(0.0F, 0.0F);
      bufferBuilder.addVertex(1.0F, 0.0F, 0.0F).setUv(1.0F, 0.0F);
      bufferBuilder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
      bufferBuilder.addVertex(0.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);
      MeshData meshData = bufferBuilder.build();
      this.quad = RenderSystem.getDevice().createBuffer(() -> "Quad", 40, meshData.vertexBuffer());
      meshData.close();
      x.close();
   }

   public static int init() {
      return -1;
   }

   public GpuBuffer getQuad() {
      return this.quad;
   }

   public void bind() {
      FrontendRenderPipeline frontend = (FrontendRenderPipeline)RenderSystem.getCompiledPipeline(CompositeRenderer.COMPOSITE_PIPELINE);
      GlRenderPipeline backend = (GlRenderPipeline)frontend.backendRenderPipeline();
      backend.vertexArray().bind(new GpuBufferSlice[]{this.quad.slice()});
   }
}
