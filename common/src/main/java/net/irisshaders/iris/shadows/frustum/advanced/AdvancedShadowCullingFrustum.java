package net.irisshaders.iris.shadows.frustum.advanced;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import java.lang.management.ManagementFactory;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider;
import net.irisshaders.iris.shadows.frustum.BoxCuller;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class AdvancedShadowCullingFrustum extends Frustum implements ViewportProvider, net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum {
   private static final int MAX_CLIPPING_PLANES = 13;
   protected final BoxCuller boxCuller;
   private final float[][] planes = new float[13][4];
   private final Vector3f shadowLightVectorFromOrigin;
   private final Vector3d position = new Vector3d();
   public double x;
   public double y;
   public double z;
   private int planeCount = 0;
   private static final boolean FMA_SUPPORT;
   public static final float CHUNK_SECTION_RADIUS = 8.0F;
   public static final float CHUNK_SECTION_MARGIN = 1.125F;
   public static final float SECTION_HALF_SIZE = 9.125F;

   public AdvancedShadowCullingFrustum(Matrix4fc modelViewProjection, Matrix4fc shadowProjection, Vector3f shadowLightVectorFromOrigin, BoxCuller boxCuller) {
      super(new Matrix4f(), new Matrix4f());
      this.shadowLightVectorFromOrigin = shadowLightVectorFromOrigin;
      BaseClippingPlanes baseClippingPlanes = new BaseClippingPlanes(modelViewProjection);
      boolean[] isBack = this.addBackPlanes(baseClippingPlanes);
      this.addEdgePlanes(baseClippingPlanes, isBack);
      this.boxCuller = boxCuller;
   }

   private void addPlane(float[] plane) {
      this.planes[this.planeCount] = plane;
      this.planeCount++;
   }

   private boolean[] addBackPlanes(BaseClippingPlanes baseClippingPlanes) {
      Vector4f[] planes = baseClippingPlanes.getPlanes();
      boolean[] isBack = new boolean[planes.length];

      for (int planeIndex = 0; planeIndex < planes.length; planeIndex++) {
         Vector4f plane = planes[planeIndex];
         Vector3f planeNormal = this.truncate(plane);
         float dot = planeNormal.dot(this.shadowLightVectorFromOrigin);
         boolean back = dot > 0.0;
         boolean edge = dot == 0.0;
         isBack[planeIndex] = back;
         if (back || edge) {
            this.addPlane(new float[]{plane.x, plane.y, plane.z, plane.w});
         }
      }

      return isBack;
   }

   private void addEdgePlanes(BaseClippingPlanes baseClippingPlanes, boolean[] isBack) {
      Vector4f[] planes = baseClippingPlanes.getPlanes();

      for (int planeIndex = 0; planeIndex < planes.length; planeIndex++) {
         if (isBack[planeIndex]) {
            Vector4f plane = planes[planeIndex];
            NeighboringPlaneSet neighbors = NeighboringPlaneSet.forPlane(planeIndex);
            if (!isBack[neighbors.plane0()]) {
               this.addEdgePlane(plane, planes[neighbors.plane0()]);
            }

            if (!isBack[neighbors.plane1()]) {
               this.addEdgePlane(plane, planes[neighbors.plane1()]);
            }

            if (!isBack[neighbors.plane2()]) {
               this.addEdgePlane(plane, planes[neighbors.plane2()]);
            }

            if (!isBack[neighbors.plane3()]) {
               this.addEdgePlane(plane, planes[neighbors.plane3()]);
            }
         }
      }
   }

   private Vector3f truncate(Vector4f base) {
      return new Vector3f(base.x(), base.y(), base.z());
   }

   private Vector4f extend(Vector3f base, float w) {
      return new Vector4f(base.x(), base.y(), base.z(), w);
   }

   private float lengthSquared(Vector3f v) {
      float x = v.x();
      float y = v.y();
      float z = v.z();
      return x * x + y * y + z * z;
   }

   private Vector3f cross(Vector3f first, Vector3f second) {
      Vector3f result = new Vector3f(first.x(), first.y(), first.z());
      result.cross(second);
      return result;
   }

   private void addEdgePlane(Vector4f backPlane4, Vector4f frontPlane4) {
      Vector3f backPlaneNormal = this.truncate(backPlane4);
      Vector3f frontPlaneNormal = this.truncate(frontPlane4);
      Vector3f intersection = this.cross(backPlaneNormal, frontPlaneNormal);
      Vector3f edgePlaneNormal = this.cross(intersection, this.shadowLightVectorFromOrigin);
      Vector3f ixb = this.cross(intersection, backPlaneNormal);
      Vector3f fxi = this.cross(frontPlaneNormal, intersection);
      ixb.mul(-frontPlane4.w());
      fxi.mul(-backPlane4.w());
      ixb.add(fxi);
      Vector3f point = ixb;
      point.mul(1.0F / this.lengthSquared(intersection));
      float d = edgePlaneNormal.dot(point);
      float w = -d;
      Vector4f plane = this.extend(edgePlaneNormal, w);
      this.addPlane(new float[]{plane.x, plane.y, plane.z, plane.w});
   }

   public void prepare(double cameraX, double cameraY, double cameraZ) {
      if (this.boxCuller != null) {
         this.boxCuller.setPosition(cameraX, cameraY, cameraZ);
      }

      this.x = cameraX;
      this.y = cameraY;
      this.z = cameraZ;
   }

   public boolean isVisible(AABB aabb) {
      return this.boxCuller != null && this.boxCuller.isCulled(aabb)
         ? false
         : this.isVisible(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ) != -3;
   }

   public int fastAabbTest(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      return this.boxCuller != null && this.boxCuller.isCulled(minX, minY, minZ, maxX, maxY, maxZ) ? 0 : this.isVisible(minX, minY, minZ, maxX, maxY, maxZ);
   }

   public boolean canDetermineInvisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      return false;
   }

   protected int isVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      float f = (float)(minX - this.x);
      float g = (float)(minY - this.y);
      float h = (float)(minZ - this.z);
      float i = (float)(maxX - this.x);
      float j = (float)(maxY - this.y);
      float k = (float)(maxZ - this.z);
      return this.checkCornerVisibility(f, g, h, i, j, k);
   }

   private static float safeFMA(float a, float b, float c) {
      return a * b + c;
   }

   protected int checkCornerVisibility(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      boolean inside = true;

      for (int i = 0; i < this.planeCount; i++) {
         float[] plane = this.planes[i];
         float outsideBoundX = plane[0] < 0.0F ? minX : maxX;
         float outsideBoundY = plane[1] < 0.0F ? minY : maxY;
         float outsideBoundZ = plane[2] < 0.0F ? minZ : maxZ;
         if (FMA_SUPPORT) {
            if (!(Math.fma(plane[0], outsideBoundX, Math.fma(plane[1], outsideBoundY, plane[2] * outsideBoundZ)) >= -plane[3])) {
               return -3;
            }

            inside &= Math.fma(
                  plane[0],
                  plane[0] < 0.0F ? maxX : minX,
                  Math.fma(plane[1], plane[1] < 0.0F ? maxY : minY, Math.fma(plane[2], plane[2] < 0.0F ? maxZ : minZ, plane[3]))
               )
               >= 0.0F;
         } else {
            if (!(safeFMA(plane[0], outsideBoundX, safeFMA(plane[1], outsideBoundY, plane[2] * outsideBoundZ)) >= -plane[3])) {
               return -3;
            }

            inside &= safeFMA(
                  plane[0],
                  plane[0] < 0.0F ? maxX : minX,
                  safeFMA(plane[1], plane[1] < 0.0F ? maxY : minY, safeFMA(plane[2], plane[2] < 0.0F ? maxZ : minZ, plane[3]))
               )
               >= 0.0F;
         }
      }

      return inside ? -2 : -1;
   }

   public boolean checkCornerVisibilityBool(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      for (int i = 0; i < this.planeCount; i++) {
         float[] plane = this.planes[i];
         float outsideBoundX = plane[0] < 0.0F ? minX : maxX;
         float outsideBoundY = plane[1] < 0.0F ? minY : maxY;
         float outsideBoundZ = plane[2] < 0.0F ? minZ : maxZ;
         if (Math.fma(plane[0], outsideBoundX, Math.fma(plane[1], outsideBoundY, plane[2] * outsideBoundZ)) < -plane[3]) {
            return false;
         }
      }

      return true;
   }

   public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      return (this.boxCuller == null || !this.boxCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ))
         && this.checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ) != -3;
   }

   public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      if (this.boxCuller == null) {
         return this.checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ);
      } else {
         int distanceResult = this.boxCuller.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
         if (distanceResult == -3) {
            return -3;
         } else {
            int frustumResult = this.checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ);
            if (frustumResult == -3) {
               return -3;
            } else {
               return frustumResult == -2 && distanceResult == -2 ? -2 : -1;
            }
         }
      }
   }

   public Viewport sodium$createViewport() {
      return new Viewport(this, this.position.set(this.x, this.y, this.z));
   }

   public boolean testSection(float x, float y, float z) {
      float minX = x - 9.125F;
      float maxX = x + 9.125F;
      float minY = y - 9.125F;
      float maxY = y + 9.125F;
      float minZ = z - 9.125F;
      float maxZ = z + 9.125F;

      for (int i = 0; i < this.planeCount; i++) {
         float[] plane = this.planes[i];
         float testX = plane[0] < 0.0F ? minX : maxX;
         float testY = plane[1] < 0.0F ? minY : maxY;
         float testZ = plane[2] < 0.0F ? minZ : maxZ;
         if (Math.fma(plane[0], testX, Math.fma(plane[1], testY, plane[2] * testZ)) < -plane[3]) {
            return false;
         }
      }

      return true;
   }

   public boolean testSectionExpanded(float originX, float originY, float originZ, float extend) {
      float minX = originX - extend;
      float maxX = originX + extend;
      float minY = originY - extend;
      float maxY = originY + extend;
      float minZ = originZ - extend;
      float maxZ = originZ + extend;

      for (int i = 0; i < this.planeCount; i++) {
         float[] plane = this.planes[i];
         float testX = plane[0] < 0.0F ? minX : maxX;
         float testY = plane[1] < 0.0F ? minY : maxY;
         float testZ = plane[2] < 0.0F ? minZ : maxZ;
         if (Math.fma(plane[0], testX, Math.fma(plane[1], testY, plane[2] * testZ)) < -plane[3]) {
            return false;
         }
      }

      return true;
   }

   static {
      HotSpotDiagnosticMXBean hotSpotDiagnostic = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
      if (hotSpotDiagnostic == null) {
         FMA_SUPPORT = false;
      } else {
         VMOption useFMAVMOption = hotSpotDiagnostic.getVMOption("UseFMA");
         FMA_SUPPORT = Boolean.parseBoolean(useFMAVMOption.getValue());
      }
   }
}
