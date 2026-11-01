package net.irisshaders.iris.uniforms;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import java.util.Objects;
import java.util.stream.StreamSupport;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.helpers.JomlConversions;
import net.irisshaders.iris.mixin.GameRendererAccessor;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class IrisExclusiveUniforms {
   private static final Vector3d ZERO = new Vector3d(0.0);

   public static void addIrisExclusiveUniforms(UniformHolder uniforms, FrameUpdateNotifier updateNotifier) {
      IrisExclusiveUniforms.WorldInfoUniforms.addWorldInfoUniforms(uniforms);
      EndFlashStorage endFlashStorage = new EndFlashStorage();
      updateNotifier.addListener(endFlashStorage::tick);
      uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "currentColorSpace", () -> IrisVideoSettings.colorSpace.ordinal());
      uniforms.uniform1f(
         UniformUpdateFrequency.PER_FRAME,
         "chunkFadeTimeInv",
         () -> (float)(1.0 / ((Double)Minecraft.getInstance().options.chunkSectionFadeInTime().get() * 1000.0))
      );
      uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "textureFilteringMode", () -> {
         return switch ((TextureFilteringMethod)Minecraft.getInstance().options.textureFiltering().get()) {
            case NONE -> 0;
            case RGSS -> 1;
            case ANISOTROPIC -> 2;
            default -> throw new MatchException(null, null);
         };
      });
      uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "feetInWater", IrisExclusiveUniforms::getIsInShallowWater);
      uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "inSwimmingAnimation", IrisExclusiveUniforms::getIsSwimming);
      uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isRiding", IrisExclusiveUniforms::getIsPassenger);
      uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isElytraFlying", IrisExclusiveUniforms::isElytraFlying);
      uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "vehicleInWater", IrisExclusiveUniforms::getVehicleInShallowWater);
      uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "vehicleId", IrisExclusiveUniforms::getVehicleId);
      uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "vehicleLookVector", IrisExclusiveUniforms::getVehicleLookVector);
      uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "relativeVehiclePosition", IrisExclusiveUniforms::getRelativeVehiclePosition);
      uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "thunderStrength", IrisExclusiveUniforms::getThunderStrength);
      uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "glintAlpha", () -> (Double)Minecraft.getInstance().options.glintStrength().get());
      uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHealth", IrisExclusiveUniforms::getCurrentHealth);
      uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "heavyFog", IrisExclusiveUniforms::isHeavyFog);
      uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHealth", IrisExclusiveUniforms::getMaxHealth);
      uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHunger", IrisExclusiveUniforms::getCurrentHunger);
      uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHunger", () -> 20);
      uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "endFlashIntensity", endFlashStorage::getCurrentEndFlash);
      uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "previousEndFlashIntensity", endFlashStorage::getLastEndFlash);
      uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerArmor", IrisExclusiveUniforms::getCurrentArmor);
      uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerArmor", () -> 50);
      uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerAir", IrisExclusiveUniforms::getCurrentAir);
      uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerAir", IrisExclusiveUniforms::getMaxAir);
      uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "firstPersonCamera", IrisExclusiveUniforms::isFirstPersonCamera);
      uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isSpectator", IrisExclusiveUniforms::isSpectator);
      uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "currentSelectedBlockId", IrisExclusiveUniforms::getCurrentSelectedBlockId);
      uniforms.uniform1i(
         UniformUpdateFrequency.PER_FRAME, "seaLevel", () -> Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getSeaLevel()
      );
      uniforms.uniform3f(UniformUpdateFrequency.PER_FRAME, "currentSelectedBlockPos", IrisExclusiveUniforms::getCurrentSelectedBlockPos);
      uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "eyePosition", IrisExclusiveUniforms::getEyePosition);
      uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "cloudTime", CapturedRenderingState.INSTANCE::getCloudTime);
      uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "relativeEyePosition", () -> CameraUniforms.getUnshiftedCameraPosition().sub(getEyePosition()));
      uniforms.uniform3d(
         UniformUpdateFrequency.PER_FRAME,
         "playerLookVector",
         () -> Minecraft.getInstance().getCameraEntity() instanceof LivingEntity livingEntity
            ? JomlConversions.fromVec3(livingEntity.getViewVector(CapturedRenderingState.INSTANCE.getTickDelta()))
            : ZERO
      );
      uniforms.uniform3d(
         UniformUpdateFrequency.PER_FRAME, "playerBodyVector", () -> JomlConversions.fromVec3(Minecraft.getInstance().getCameraEntity().getForward())
      );
      Vector4f zero = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
      uniforms.uniform4f(
         UniformUpdateFrequency.PER_TICK,
         "lightningBoltPosition",
         () -> Minecraft.getInstance().level != null
            ? StreamSupport.<Entity>stream(Minecraft.getInstance().level.entitiesForRendering().spliterator(), false)
               .filter(bolt -> bolt instanceof LightningBolt)
               .findAny()
               .map(
                  bolt -> {
                     Vector3d unshiftedCameraPosition = CameraUniforms.getUnshiftedCameraPosition();
                     Vec3 vec3 = bolt.getPosition(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
                     return new Vector4f(
                        (float)(vec3.x - unshiftedCameraPosition.x),
                        (float)(vec3.y - unshiftedCameraPosition.y),
                        (float)(vec3.z - unshiftedCameraPosition.z),
                        1.0F
                     );
                  }
               )
               .orElse(zero)
            : zero
      );
   }

   private static int getVehicleId() {
      if (Minecraft.getInstance().player == null) {
         return 0;
      }

      if (Minecraft.getInstance().player.getVehicle() == null) {
         return 0;
      }

      Object2IntFunction<NamespacedId> entityId = WorldRenderingSettings.INSTANCE.getEntityIds();
      if (entityId == null) {
         return 0;
      }

      Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(Minecraft.getInstance().player.getVehicle().getType());
      return id == null ? 0 : entityId.applyAsInt(new NamespacedId(id.getNamespace(), id.getPath()));
   }

   private static Vector3d getVehicleLookVector() {
      if (Minecraft.getInstance().player == null) {
         return ZERO;
      } else {
         return Minecraft.getInstance().player.getVehicle() == null ? ZERO : JomlConversions.fromVec3(Minecraft.getInstance().player.getVehicle().getForward());
      }
   }

   private static Vector3d getRelativeVehiclePosition() {
      if (Minecraft.getInstance().player == null) {
         return ZERO;
      }

      if (Minecraft.getInstance().player.getVehicle() == null) {
         return ZERO;
      }

      Vec3 vehiclePos = Minecraft.getInstance().player.getVehicle().getPosition(CapturedRenderingState.INSTANCE.getTickDelta());
      Vector3d pos = new Vector3d(vehiclePos.x, vehiclePos.y, vehiclePos.z);
      return CameraUniforms.getUnshiftedCameraPosition().sub(pos);
   }

   private static boolean getVehicleInShallowWater() {
      if (Minecraft.getInstance().player == null) {
         return false;
      } else {
         return Minecraft.getInstance().player.getVehicle() == null ? false : Minecraft.getInstance().player.getVehicle().isInShallowWater();
      }
   }

   private static boolean getIsInShallowWater() {
      return Minecraft.getInstance().player == null ? false : Minecraft.getInstance().player.isInShallowWater();
   }

   private static boolean getIsSwimming() {
      return Minecraft.getInstance().player == null ? false : Minecraft.getInstance().player.isSwimming();
   }

   private static boolean getIsPassenger() {
      return Minecraft.getInstance().player == null ? false : Minecraft.getInstance().player.isPassenger();
   }

   private static boolean isElytraFlying() {
      return Minecraft.getInstance().player == null ? false : Minecraft.getInstance().player.isFallFlying();
   }

   private static boolean isHeavyFog() {
      if (Minecraft.getInstance().level != null) {
         Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
         return Minecraft.getInstance().gui.hud.getBossOverlay().shouldCreateWorldFog();
      } else {
         return false;
      }
   }

   private static int getCurrentSelectedBlockId() {
      HitResult hitResult = Minecraft.getInstance().hitResult;
      if (Minecraft.getInstance().level != null
         && ((GameRendererAccessor)Minecraft.getInstance().gameRenderer).shouldRenderBlockOutlineA()
         && hitResult != null
         && hitResult.getType() == Type.BLOCK) {
         BlockPos blockPos4 = ((BlockHitResult)hitResult).getBlockPos();
         BlockState blockState = Minecraft.getInstance().level.getBlockState(blockPos4);
         if (!blockState.isAir() && Minecraft.getInstance().level.getWorldBorder().isWithinBounds(blockPos4)) {
            return WorldRenderingSettings.INSTANCE.getBlockStateIds().getInt(blockState);
         }
      }

      return 0;
   }

   private static Vector3f getCurrentSelectedBlockPos() {
      HitResult hitResult = Minecraft.getInstance().hitResult;
      if (Minecraft.getInstance().level != null
         && ((GameRendererAccessor)Minecraft.getInstance().gameRenderer).shouldRenderBlockOutlineA()
         && hitResult != null
         && hitResult.getType() == Type.BLOCK) {
         BlockPos blockPos4 = ((BlockHitResult)hitResult).getBlockPos();
         return new Vec3(blockPos4.getX() + 0.5, blockPos4.getY() + 0.5, blockPos4.getZ() + 0.5)
            .subtract(Minecraft.getInstance().gameRenderer.mainCamera().position())
            .toVector3f();
      } else {
         return new Vector3f(-256.0F);
      }
   }

   private static float getThunderStrength() {
      return Math.clamp(0.0F, 1.0F, Minecraft.getInstance().level.getThunderLevel(CapturedRenderingState.INSTANCE.getTickDelta()));
   }

   private static float getCurrentHealth() {
      return Minecraft.getInstance().player != null && Minecraft.getInstance().gameMode.getPlayerMode().isSurvival()
         ? Minecraft.getInstance().player.getHealth() / Minecraft.getInstance().player.getMaxHealth()
         : -1.0F;
   }

   private static float getCurrentHunger() {
      return Minecraft.getInstance().player != null && Minecraft.getInstance().gameMode.getPlayerMode().isSurvival()
         ? Minecraft.getInstance().player.getFoodData().getFoodLevel() / 20.0F
         : -1.0F;
   }

   private static float getCurrentAir() {
      return Minecraft.getInstance().player != null && Minecraft.getInstance().gameMode.getPlayerMode().isSurvival()
         ? (float)Minecraft.getInstance().player.getAirSupply() / Minecraft.getInstance().player.getMaxAirSupply()
         : -1.0F;
   }

   private static float getCurrentArmor() {
      return Minecraft.getInstance().player != null && Minecraft.getInstance().gameMode.getPlayerMode().isSurvival()
         ? Minecraft.getInstance().player.getArmorValue() / 50.0F
         : -1.0F;
   }

   private static float getMaxAir() {
      return Minecraft.getInstance().player != null && Minecraft.getInstance().gameMode.getPlayerMode().isSurvival()
         ? Minecraft.getInstance().player.getMaxAirSupply()
         : -1.0F;
   }

   private static float getMaxHealth() {
      return Minecraft.getInstance().player != null && Minecraft.getInstance().gameMode.getPlayerMode().isSurvival()
         ? Minecraft.getInstance().player.getMaxHealth()
         : -1.0F;
   }

   private static boolean isFirstPersonCamera() {
      return switch (Minecraft.getInstance().options.getCameraType()) {
         case THIRD_PERSON_BACK, THIRD_PERSON_FRONT -> false;
         default -> true;
      };
   }

   private static boolean isSpectator() {
      return Minecraft.getInstance().gameMode.getPlayerMode() == GameType.SPECTATOR;
   }

   private static Vector3d getEyePosition() {
      Objects.requireNonNull(Minecraft.getInstance().getCameraEntity());
      Vec3 pos = Minecraft.getInstance().getCameraEntity().getEyePosition(CapturedRenderingState.INSTANCE.getTickDelta());
      return new Vector3d(pos.x, pos.y, pos.z);
   }

   public static class WorldInfoUniforms {
      public static void addWorldInfoUniforms(UniformHolder uniforms) {
         ClientLevel level = Minecraft.getInstance().level;
         uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "bedrockLevel", () -> level != null ? level.dimensionType().minY() : 0);
         uniforms.uniform1f(
            UniformUpdateFrequency.PER_FRAME,
            "cloudHeight",
            () -> level != null
               ? ((Float)Minecraft.getInstance()
                     .gameRenderer
                     .mainCamera()
                     .attributeProbe()
                     .getValue(EnvironmentAttributes.CLOUD_HEIGHT, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)))
                  .floatValue()
               : 192.0
         );
         uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "heightLimit", () -> level != null ? level.dimensionType().height() : 256);
         uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "logicalHeightLimit", () -> level != null ? level.dimensionType().logicalHeight() : 256);
         uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hasCeiling", () -> level != null ? level.dimensionType().hasCeiling() : false);
         uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hasSkylight", () -> level != null ? level.dimensionType().hasSkyLight() : true);
         uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "ambientLight", () -> level != null ? level.dimensionType().ambientLight() : 0.0F);
      }
   }
}
