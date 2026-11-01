package net.irisshaders.iris.shadows;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

public class ShadowMatrices {
   public static final float NEAR = -100.05F;
   public static final float FAR = 156.0F;

   public static Matrix4f createOrthoMatrix(float halfPlaneLength, float nearPlane, float farPlane) {
      return new Matrix4f()
         .setOrthoSymmetric(halfPlaneLength * 2.0F, halfPlaneLength * 2.0F, nearPlane, farPlane, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
   }

   public static Matrix4f createPerspectiveMatrix(float fov) {
      boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
      float yScale = (float)(1.0 / Math.tan(Math.toRadians(fov) * 0.5));
      return new Matrix4f(
         yScale,
         0.0F,
         0.0F,
         0.0F,
         0.0F,
         yScale,
         0.0F,
         0.0F,
         0.0F,
         0.0F,
         (zZeroToOne ? 156.0F : 55.949997F) / -256.05F,
         -1.0F,
         0.0F,
         0.0F,
         (RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 156.0F : 312.0F) * -100.05F / -256.05F,
         1.0F
      );
   }

   public static void createBaselineModelViewMatrix(PoseStack target, float shadowAngle, float sunPathRotation, float nearPlane, float farPlane) {
      target.last().normal().identity();
      target.last().pose().identity();
      if (Minecraft.getInstance().level.dimension() == Level.END
         && Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::supportsEndFlash).orElse(false)) {
         EndFlashState state = Minecraft.getInstance().level.endFlashState();
         float skyAngle = state.getXAngle();
         float h = state.getYAngle();
         target.rotateDegrees(Axis.XP, 0.0F - skyAngle);
         target.rotateDegrees(Axis.YP, h);
      } else {
         float skyAngle;
         if (shadowAngle < 0.25F) {
            skyAngle = shadowAngle + 0.75F;
         } else {
            skyAngle = shadowAngle - 0.25F;
         }

         target.rotateDegrees(Axis.XP, 90.0F);
         target.rotateDegrees(Axis.ZP, skyAngle * -360.0F);
         target.rotateDegrees(Axis.XP, sunPathRotation);
      }
   }

   public static void snapModelViewToGrid(PoseStack target, float shadowIntervalSize, double cameraX, double cameraY, double cameraZ) {
      if (Math.abs(shadowIntervalSize) != 0.0F) {
         float offsetX = (float)cameraX % shadowIntervalSize;
         float offsetY = (float)cameraY % shadowIntervalSize;
         float offsetZ = (float)cameraZ % shadowIntervalSize;
         float halfIntervalSize = shadowIntervalSize / 2.0F;
         offsetX -= halfIntervalSize;
         offsetY -= halfIntervalSize;
         offsetZ -= halfIntervalSize;
         target.last().pose().translate(offsetX, offsetY, offsetZ);
      }
   }

   public static void createModelViewMatrix(
      PoseStack target,
      float shadowAngle,
      float shadowIntervalSize,
      float sunPathRotation,
      double cameraX,
      double cameraY,
      double cameraZ,
      float nearPlane,
      float farPlane
   ) {
      createBaselineModelViewMatrix(target, shadowAngle, sunPathRotation, nearPlane, farPlane);
      snapModelViewToGrid(target, shadowIntervalSize, cameraX, cameraY, cameraZ);
   }

   private static final class Tests {
      public static void main(String[] args) {
         Matrix4f expected = new Matrix4f(
            0.03125F, 0.0F, 0.0F, 0.0F, 0.0F, 0.03125F, 0.0F, 0.0F, 0.0F, 0.0F, -0.007814026F, 0.0F, 0.0F, 0.0F, -1.0003906F, 1.0F
         );
         test("ortho projection hpl=32", expected, ShadowMatrices.createOrthoMatrix(32.0F, 0.05F, 256.0F));
         Matrix4f expected110 = new Matrix4f(
            0.009090909F, 0.0F, 0.0F, 0.0F, 0.0F, 0.009090909F, 0.0F, 0.0F, 0.0F, 0.0F, -0.007814026F, 0.0F, 0.0F, 0.0F, -1.0003906F, 1.0F
         );
         test("ortho projection hpl=110", expected110, ShadowMatrices.createOrthoMatrix(110.0F, 0.05F, 256.0F));
         Matrix4f expected90Proj = new Matrix4f(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0003906F, -1.0F, 0.0F, 0.0F, -0.10001954F, 0.0F);
         test("perspective projection fov=90", expected90Proj, ShadowMatrices.createPerspectiveMatrix(90.0F));
         Matrix4f expectedModelViewAtDawn = new Matrix4f(
            0.2154504F,
            5.8204815E-8F,
            0.9765147F,
            0.0F,
            -0.97651476F,
            1.2841845E-8F,
            0.21545039F,
            0.0F,
            0.0F,
            -0.99999994F,
            5.9604645E-8F,
            0.0F,
            0.3800215F,
            1.0264281F,
            -100.44631F,
            1.0F
         );
         PoseStack modelView = new PoseStack();
         ShadowMatrices.createModelViewMatrix(modelView, 0.03451777F, 2.0F, 0.0F, 0.646046F, 82.532745F, -514.0264F, -100.05F, 156.0F);
         test("model view at dawn", expectedModelViewAtDawn, modelView.last().pose());
      }

      private static void test(String name, Matrix4f expected, Matrix4f created) {
         if (expected.equals(created, 5.0E-4F)) {
            System.err.println("test " + name + " failed: ");
            System.err.println("    expected: ");
            System.err.print(expected);
            System.err.println("    created: ");
            System.err.print(created.toString());
         } else {
            System.out.println("test " + name + " passed");
         }
      }
   }
}
