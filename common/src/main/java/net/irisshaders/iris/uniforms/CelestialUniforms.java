package net.irisshaders.iris.uniforms;

import com.mojang.math.Axis;
import java.util.Objects;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class CelestialUniforms {
   private static final Vector4f ZERO = new Vector4f();
   private final float sunPathRotation;

   public CelestialUniforms(float sunPathRotation) {
      this.sunPathRotation = sunPathRotation;
   }

   public static float getSunAngle(boolean sun) {
      float currentAngle = (Float)Minecraft.getInstance()
         .gameRenderer
         .mainCamera()
         .attributeProbe()
         .getValue(sun ? EnvironmentAttributes.SUN_ANGLE : EnvironmentAttributes.MOON_ANGLE, CapturedRenderingState.INSTANCE.getTickDelta());
      float c = currentAngle + 90.0F;
      if (c < 0.0F) {
         c += 360.0F;
      } else if (c > 360.0F) {
         c -= 360.0F;
      }

      return c;
   }

   private static float getShadowAngle() {
      float shadowAngle = getSunAngle(isDay());
      return shadowAngle / 360.0F;
   }

   private static Vector4f getUpPosition() {
      Vector4f upVector = new Vector4f(0.0F, 100.0F, 0.0F, 0.0F);
      Matrix4f preCelestial = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());
      preCelestial.rotate(Axis.YP.rotationDegrees(-90.0F));
      return preCelestial.transform(upVector);
   }

   public static boolean isDay() {
      float sunAngle = getSunAngle(true);
      return sunAngle < 180.0F;
   }

   private static ClientLevel getWorld() {
      return Objects.requireNonNull(Minecraft.getInstance().level);
   }

   public void addCelestialUniforms(UniformHolder uniforms) {
      uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "sunAngle", () -> getSunAngle(true) / 360.0F)
         .uniformTruncated3f(UniformUpdateFrequency.PER_FRAME, "sunPosition", this::getSunPosition)
         .uniformTruncated3f(UniformUpdateFrequency.PER_FRAME, "moonPosition", this::getMoonPosition)
         .uniform1f(UniformUpdateFrequency.PER_FRAME, "shadowAngle", CelestialUniforms::getShadowAngle)
         .uniformTruncated3f(UniformUpdateFrequency.PER_FRAME, "shadowLightPosition", this::getShadowLightPosition)
         .uniformTruncated3f(
            UniformUpdateFrequency.PER_FRAME,
            "endFlashPosition",
            () -> Minecraft.getInstance().level.dimension() == Level.END ? this.getEndFlashPosition() : ZERO
         )
         .uniformTruncated3f(UniformUpdateFrequency.PER_FRAME, "upPosition", CelestialUniforms::getUpPosition);
   }

   private Vector4f getSunPosition() {
      return this.getCelestialPosition(true, 100.0F);
   }

   private Vector4f getMoonPosition() {
      return this.getCelestialPosition(false, -100.0F);
   }

   private Vector4f getEndFlashPosition() {
      EndFlashState state = Minecraft.getInstance().level.endFlashState();
      if (state == null) {
         return ZERO;
      }

      float h = state.getYAngle();
      float g = state.getXAngle();
      Vector4f pos = new Vector4f(0.0F, 100.0F, 0.0F, 0.0F);
      Matrix4f m = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());
      m.rotate(Axis.YP.rotationDegrees(180.0F - h));
      m.rotate(Axis.XP.rotationDegrees(-90.0F - g));
      return m.transform(pos);
   }

   private Vector4f getEndFlashPositionInWorldSpace() {
      EndFlashState state = Minecraft.getInstance().level.endFlashState();
      float h = state.getYAngle();
      float g = state.getXAngle();
      Vector4f pos = new Vector4f(0.0F, 100.0F, 0.0F, 0.0F);
      Matrix4f m = new Matrix4f();
      m.identity();
      m.rotate(Axis.YP.rotationDegrees(180.0F - h));
      m.rotate(Axis.XP.rotationDegrees(-90.0F - g));
      return m.transform(pos);
   }

   public Vector4f getShadowLightPosition() {
      if (Minecraft.getInstance().level.dimension() == Level.END
         && Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::supportsEndFlash).orElse(false)) {
         return this.getEndFlashPosition();
      } else {
         return isDay() ? this.getSunPosition() : this.getMoonPosition();
      }
   }

   public Vector4f getShadowLightPositionInWorldSpace() {
      if (Minecraft.getInstance().level.dimension() == Level.END
         && Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::supportsEndFlash).orElse(false)) {
         return this.getEndFlashPositionInWorldSpace();
      } else {
         return isDay() ? this.getCelestialPositionInWorldSpace(true, 100.0F) : this.getCelestialPositionInWorldSpace(false, -100.0F);
      }
   }

   private Vector4f getCelestialPositionInWorldSpace(boolean sun, float y) {
      Vector4f position = new Vector4f(0.0F, 100.0F, 0.0F, 0.0F);
      Matrix4f celestial = new Matrix4f();
      celestial.identity();
      celestial.rotate(Axis.YP.rotationDegrees(-90.0F));
      celestial.rotate(Axis.ZP.rotationDegrees(this.sunPathRotation));
      float currentAngle = (Float)Minecraft.getInstance()
         .gameRenderer
         .mainCamera()
         .attributeProbe()
         .getValue(sun ? EnvironmentAttributes.SUN_ANGLE : EnvironmentAttributes.MOON_ANGLE, CapturedRenderingState.INSTANCE.getTickDelta());
      celestial.rotate(Axis.XP.rotationDegrees(currentAngle));
      celestial.transform(position);
      return position;
   }

   private Vector4f getCelestialPosition(boolean sun, float y) {
      Vector4f position = new Vector4f(0.0F, 100.0F, 0.0F, 1.0F);
      Matrix4f celestial = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());
      celestial.rotate(Axis.YP.rotationDegrees(-90.0F));
      celestial.rotate(Axis.ZP.rotationDegrees(this.sunPathRotation));
      float currentAngle = (Float)Minecraft.getInstance()
         .gameRenderer
         .mainCamera()
         .attributeProbe()
         .getValue(sun ? EnvironmentAttributes.SUN_ANGLE : EnvironmentAttributes.MOON_ANGLE, CapturedRenderingState.INSTANCE.getTickDelta());
      celestial.rotate(Axis.XP.rotationDegrees(currentAngle));
      return celestial.transform(position);
   }
}
