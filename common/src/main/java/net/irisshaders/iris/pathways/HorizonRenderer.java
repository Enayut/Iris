package net.irisshaders.iris.pathways;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class HorizonRenderer {
   private static final float TOP = 16.0F;
   private static final float BOTTOM = -16.0F;
   private final ByteBufferBuilder storage;
   private GpuBuffer buffer;
   private int currentRenderDistance;
   private int indexCount = -1;

   public HorizonRenderer() {
      this.currentRenderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
      this.storage = new ByteBufferBuilder(1024);
      this.rebuildBuffer();
   }

   private void rebuildBuffer() {
      if (this.buffer != null) {
         this.buffer.close();
      }

      BufferBuilder buffer = new BufferBuilder(this.storage, PrimitiveTopology.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
      this.buildHorizon(this.currentRenderDistance * 16, buffer);
      MeshData meshData = buffer.buildOrThrow();
      this.buffer = RenderSystem.getDevice().createBuffer(() -> "Horizon", 40, meshData.vertexBuffer());
      this.indexCount = meshData.drawState().indexCount();
      meshData.close();
      this.storage.clear();
   }

   private void buildHorizon(int radius, VertexConsumer consumer) {
      if (radius > 256) {
         radius = 256;
      }

      consumer.addVertex(0.0F, -16.0F, 0.0F);

      for (int i = 0; i <= 8; i++) {
         float angle = (float)(-i * Math.PI / 4.0);
         float x = (float)(radius * Math.cos(angle));
         float z = (float)(radius * Math.sin(angle));
         consumer.addVertex(x, 16.0F, z);
      }
   }

   public void renderHorizon(Matrix4fc modelView, Matrix4fc projection, Vector4f fogColor) {
      if (this.currentRenderDistance != Minecraft.getInstance().options.getEffectiveRenderDistance()) {
         this.currentRenderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
         this.rebuildBuffer();
      }

      AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLE_FAN);
      GpuBuffer indexBuffer = indices.getBuffer(this.indexCount);
      GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms().writeTransform((Matrix4f)modelView, fogColor, new Vector3f(), new Matrix4f());
      RenderPass pass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(
            () -> "Sky",
            Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(),
            Optional.empty(),
            Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView(),
            OptionalDouble.empty()
         );

      try {
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("DynamicTransforms", gpuBufferSlice);
         pass.setVertexBuffer(0, this.buffer.slice());
         pass.setIndexBuffer(indexBuffer, indices.type());
         pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.SKY));
         pass.drawIndexed(this.indexCount, 1, 0, 0, 0);
      } catch (Throwable var11) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var10) {
               var11.addSuppressed(var10);
            }
         }

         throw var11;
      }

      if (pass != null) {
         pass.close();
      }
   }

   public void destroy() {
      this.buffer.close();
   }
}
