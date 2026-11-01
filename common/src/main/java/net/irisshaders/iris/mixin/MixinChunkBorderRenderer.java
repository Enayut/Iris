package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.debug.ChunkBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkBorderRenderer.class)
public class MixinChunkBorderRenderer {
   private VertexConsumer fakeConsumer = new VertexConsumer() {
      public VertexConsumer addVertex(float x, float y, float z) {
         return this;
      }

      public VertexConsumer setColor(int red, int green, int blue, int alpha) {
         return this;
      }

      public VertexConsumer setColor(int i) {
         return this;
      }

      public VertexConsumer setUv(float u, float v) {
         return this;
      }

      public VertexConsumer setUv1(int u, int v) {
         return this;
      }

      public VertexConsumer setUv2(int u, int v) {
         return this;
      }

      public VertexConsumer setUv3(float u, float v) {
         return this;
      }

      public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
         return this;
      }

      public VertexConsumer setLineWidth(float f) {
         return this;
      }
   };
}
