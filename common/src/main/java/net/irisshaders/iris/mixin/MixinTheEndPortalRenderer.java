package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import org.joml.Matrix3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractEndPortalRenderer.class)
public class MixinTheEndPortalRenderer {
   @Shadow
   @Final
   private static Map<Direction, List<Vector3fc>> FACES;
   @Unique
   private static final float RED = 0.075F;
   @Unique
   private static final float GREEN = 0.15F;
   @Unique
   private static final float BLUE = 0.2F;

   @ModifyArg(
      method = "submitCube(Ljava/util/Collection;Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Ljava/util/function/Consumer;)V",
      index = 1,
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"
      )
   )
   private static RenderType iris$renderType(RenderType par2) {
      return Iris.getCurrentPack().isPresent() ? RenderTypes.entitySolid(TheEndPortalRenderer.END_PORTAL_LOCATION) : par2;
   }

   @Inject(method = "lambda$submitCube$1", at = @At("HEAD"), cancellable = true, require = 1)
   private static <T extends TheEndPortalBlockEntity> void iris$onRender(
      Collection<Direction> facesToShow, Consumer vertexDecorator, Pose pose, VertexConsumer buffer, CallbackInfo ci
   ) {
      if (!Iris.getCurrentPack().isEmpty()) {
         int overlay = OverlayTexture.NO_OVERLAY;
         int light = 15728880;
         ci.cancel();
         Matrix3f normal = pose.normal();
         float progress = SystemTimeUniforms.TIMER.getFrameTimeCounter() * 0.01F % 1.0F;

         for (Direction direction : facesToShow) {
            float nx = direction.getStepX();
            float ny = direction.getStepY();
            float nz = direction.getStepZ();
            List<Vector3fc> vertices = FACES.get(direction);
            Vector3fc vertex0 = vertices.get(0);
            buffer.addVertex(pose, vertex0.x(), vertex0.y(), vertex0.z())
               .setColor(0.075F, 0.15F, 0.2F, 1.0F)
               .setUv(0.0F + progress, 0.0F + progress)
               .setOverlay(overlay)
               .setLight(light)
               .setNormal(pose, nx, ny, nz);
            Vector3fc vertex1 = vertices.get(1);
            buffer.addVertex(pose, vertex1.x(), vertex1.y(), vertex1.z())
               .setColor(0.075F, 0.15F, 0.2F, 1.0F)
               .setUv(0.0F + progress, 0.2F + progress)
               .setOverlay(overlay)
               .setLight(light)
               .setNormal(pose, nx, ny, nz);
            Vector3fc vertex2 = vertices.get(2);
            buffer.addVertex(pose, vertex2.x(), vertex2.y(), vertex2.z())
               .setColor(0.075F, 0.15F, 0.2F, 1.0F)
               .setUv(0.2F + progress, 0.2F + progress)
               .setOverlay(overlay)
               .setLight(light)
               .setNormal(pose, nx, ny, nz);
            Vector3fc vertex3 = vertices.get(3);
            buffer.addVertex(pose, vertex3.x(), vertex3.y(), vertex3.z())
               .setColor(0.075F, 0.15F, 0.2F, 1.0F)
               .setUv(0.2F + progress, 0.0F + progress)
               .setOverlay(overlay)
               .setLight(light)
               .setNormal(pose, nx, ny, nz);
         }
      }
   }

   @Unique
   private void quad(
      EndPortalRenderState entity,
      VertexConsumer vertexConsumer,
      Pose pose,
      Matrix3f normal,
      Direction direction,
      float progress,
      int overlay,
      int light,
      float x1,
      float y1,
      float z1,
      float x2,
      float y2,
      float z2,
      float x3,
      float y3,
      float z3,
      float x4,
      float y4,
      float z4
   ) {
      if (entity.facesToShow.contains(direction)) {
         float nx = direction.getStepX();
         float ny = direction.getStepY();
         float nz = direction.getStepZ();
         vertexConsumer.addVertex(pose, x1, y1, z1)
            .setColor(0.075F, 0.15F, 0.2F, 1.0F)
            .setUv(0.0F + progress, 0.0F + progress)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
         vertexConsumer.addVertex(pose, x2, y2, z2)
            .setColor(0.075F, 0.15F, 0.2F, 1.0F)
            .setUv(0.0F + progress, 0.2F + progress)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
         vertexConsumer.addVertex(pose, x3, y3, z3)
            .setColor(0.075F, 0.15F, 0.2F, 1.0F)
            .setUv(0.2F + progress, 0.2F + progress)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
         vertexConsumer.addVertex(pose, x4, y4, z4)
            .setColor(0.075F, 0.15F, 0.2F, 1.0F)
            .setUv(0.2F + progress, 0.0F + progress)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
      }
   }
}
