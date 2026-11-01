package net.irisshaders.iris.shadows.frustum.fallback;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

public class NonCullingFrustum extends Frustum implements ViewportProvider, net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum {
   private final Vector3d position = new Vector3d();

   public NonCullingFrustum() {
      super(new Matrix4f(), new Matrix4f());
   }

   public NonCullingFrustum(Matrix4fc modelViewMatrix, Matrix4f projectionMatrixForCulling) {
      super(modelViewMatrix, projectionMatrixForCulling);
   }

   public boolean canDetermineInvisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      return false;
   }

   public boolean isVisible(AABB box) {
      return true;
   }

   public int cubeInFrustum(BoundingBox boundingBox) {
      return -2;
   }

   public boolean pointInFrustum(double x, double y, double z) {
      return true;
   }

   public double getCamX() {
      return this.position.x;
   }

   public double getCamY() {
      return this.position.y;
   }

   public double getCamZ() {
      return this.position.z;
   }

   public void prepare(double d, double e, double f) {
      super.prepare(d, e, f);
      this.position.set(d, e, f);
   }

   public Viewport sodium$createViewport() {
      return new Viewport(this, this.position);
   }

   public boolean testAab(float v, float v1, float v2, float v3, float v4, float v5) {
      return true;
   }

   public int intersectAab(float v, float v1, float v2, float v3, float v4, float v5) {
      return -2;
   }

   public boolean testSection(float v, float v1, float v2) {
      return true;
   }

   public boolean testSectionExpanded(float v, float v1, float v2, float v3) {
      return true;
   }
}
