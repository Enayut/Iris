package net.irisshaders.iris.shadows.frustum.advanced;

import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import net.irisshaders.iris.shadows.frustum.BoxCuller;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public class SafeZoneCullingFrustum extends AdvancedShadowCullingFrustum implements Frustum {
   private final BoxCuller distanceCuller;

   public SafeZoneCullingFrustum(
      Matrix4fc modelViewProjection, Matrix4fc shadowProjection, Vector3f shadowLightVectorFromOrigin, BoxCuller voxelCuller, BoxCuller distanceCuller
   ) {
      super(modelViewProjection, shadowProjection, shadowLightVectorFromOrigin, voxelCuller);
      this.distanceCuller = distanceCuller;
   }

   @Override
   public void prepare(double cameraX, double cameraY, double cameraZ) {
      if (this.distanceCuller != null) {
         this.distanceCuller.setPosition(cameraX, cameraY, cameraZ);
      }

      super.prepare(cameraX, cameraY, cameraZ);
   }

   @Override
   public boolean isVisible(AABB aabb) {
      if (this.distanceCuller != null && this.distanceCuller.isCulled(aabb)) {
         return false;
      } else {
         return this.boxCuller != null && !this.boxCuller.isCulled(aabb)
            ? true
            : this.isVisible(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ) != 0;
      }
   }

   @Override
   public int fastAabbTest(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      if (this.distanceCuller != null && this.distanceCuller.isCulled(minX, minY, minZ, maxX, maxY, maxZ)) {
         return 0;
      } else {
         return this.boxCuller != null && !this.boxCuller.isCulled(minX, minY, minZ, maxX, maxY, maxZ) ? 2 : this.isVisible(minX, minY, minZ, maxX, maxY, maxZ);
      }
   }

   @Override
   public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      if (this.distanceCuller != null && this.distanceCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ)) {
         return false;
      } else {
         return this.boxCuller != null && !this.boxCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ)
            ? true
            : this.checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ) != -3;
      }
   }

   @Override
   public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      int distanceResult = this.distanceCuller.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
      if (distanceResult == -3) {
         return -3;
      }

      int safeZoneResult = -3;
      if (this.boxCuller != null) {
         safeZoneResult = this.boxCuller.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
         if (safeZoneResult == -2) {
            return -2;
         }
      }

      if (distanceResult == -1 && safeZoneResult == -1) {
         return -1;
      } else {
         int frustumResult = this.checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ);
         if (safeZoneResult == -3 && frustumResult == -3) {
            return -3;
         } else {
            return frustumResult == -2 && distanceResult == -2 ? -2 : -1;
         }
      }
   }
}
