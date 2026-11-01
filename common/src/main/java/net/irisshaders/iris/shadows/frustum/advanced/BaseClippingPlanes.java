package net.irisshaders.iris.shadows.frustum.advanced;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public class BaseClippingPlanes {
   private final Vector4f[] planes = new Vector4f[6];

   public BaseClippingPlanes(Matrix4fc modelViewProjection) {
      this.init(modelViewProjection);
   }

   private static Vector4f transform(Matrix4fc transform, float x, float y, float z) {
      Vector4f vector4f = new Vector4f(x, y, z, 1.0F);
      vector4f.mul(transform);
      vector4f.normalize();
      return vector4f;
   }

   private void init(Matrix4fc modelViewProjection) {
      Matrix4f transform = new Matrix4f(modelViewProjection);
      transform.transpose();
      this.planes[0] = transform(transform, -1.0F, 0.0F, 0.0F);
      this.planes[1] = transform(transform, 1.0F, 0.0F, 0.0F);
      this.planes[2] = transform(transform, 0.0F, -1.0F, 0.0F);
      this.planes[3] = transform(transform, 0.0F, 1.0F, 0.0F);
      this.planes[4] = transform(transform, 0.0F, 0.0F, -1.0F);
      this.planes[5] = transform(transform, 0.0F, 0.0F, 1.0F);
   }

   public Vector4f[] getPlanes() {
      return this.planes;
   }
}
