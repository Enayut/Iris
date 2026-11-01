package net.irisshaders.iris.shadows.frustum.fallback;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider;
import net.irisshaders.iris.shadows.frustum.BoxCuller;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public class BoxCullingFrustum extends Frustum implements net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum, ViewportProvider {
   private final BoxCuller boxCuller;
   private final Vector3d position = new Vector3d();
   public static final float CHUNK_SECTION_RADIUS = 8.0F;
   public static final float CHUNK_SECTION_MARGIN = 1.125F;
   public static final float SECTION_HALF_SIZE = 9.125F;

   public BoxCullingFrustum(BoxCuller boxCuller) {
      super(new Matrix4f(), new Matrix4f());
      this.boxCuller = boxCuller;
   }

   public void prepare(double cameraX, double cameraY, double cameraZ) {
      this.position.set(cameraX, cameraY, cameraZ);
      this.boxCuller.setPosition(cameraX, cameraY, cameraZ);
   }

   public boolean canDetermineInvisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      return false;
   }

   public boolean isVisible(AABB box) {
      return !this.boxCuller.isCulled(box);
   }

   public Viewport sodium$createViewport() {
      return new Viewport(this, this.position);
   }

   public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      return !this.boxCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ);
   }

   public int intersectAab(float v, float v1, float v2, float v3, float v4, float v5) {
      return this.boxCuller.intersectAab(v, v1, v2, v3, v4, v5);
   }

   public boolean testSection(float x, float y, float z) {
      float minX = x - 9.125F;
      float maxX = x + 9.125F;
      float minY = y - 9.125F;
      float maxY = y + 9.125F;
      float minZ = z - 9.125F;
      float maxZ = z + 9.125F;
      return !this.boxCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ);
   }

   public boolean testSectionExpanded(float x, float y, float z, float extend) {
      float minX = x - extend;
      float maxX = x + extend;
      float minY = y - extend;
      float maxY = y + extend;
      float minZ = z - extend;
      float maxZ = z + extend;
      return !this.boxCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ);
   }
}
