package net.irisshaders.iris.uniforms;

import java.util.Objects;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;

public final class WorldTimeUniforms {
   private WorldTimeUniforms() {
   }

   public static void addWorldTimeUniforms(UniformHolder uniforms) {
      uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "worldTime", WorldTimeUniforms::getWorldDayTime)
         .uniform1i(UniformUpdateFrequency.PER_TICK, "worldDay", WorldTimeUniforms::getWorldDay)
         .uniform1i(
            UniformUpdateFrequency.PER_TICK,
            "moonPhase",
            () -> ((MoonPhase)Minecraft.getInstance()
                  .gameRenderer
                  .mainCamera()
                  .attributeProbe()
                  .getValue(EnvironmentAttributes.MOON_PHASE, CapturedRenderingState.INSTANCE.getTickDelta()))
               .index()
         );
   }

   static int getWorldDayTime() {
      long timeOfDay = getWorld().getDefaultClockTime();
      if (Iris.getCurrentDimension() != DimensionId.END && Iris.getCurrentDimension() != DimensionId.NETHER) {
         long dayTime = getWorld().dimensionType().hasFixedTime() ? 0L : timeOfDay % 24000L;
         return (int)dayTime;
      } else {
         return (int)(timeOfDay % 24000L);
      }
   }

   private static int getWorldDay() {
      long timeOfDay = getWorld().getDefaultClockTime();
      long day = timeOfDay / 24000L;
      return (int)day;
   }

   private static ClientLevel getWorld() {
      return Objects.requireNonNull(Minecraft.getInstance().level);
   }
}
