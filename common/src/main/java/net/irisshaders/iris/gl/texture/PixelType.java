package net.irisshaders.iris.gl.texture;

import java.util.Locale;
import java.util.Optional;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.GlVersion;

public enum PixelType {
   BYTE(1, 5120, GlVersion.GL_11),
   SHORT(2, 5122, GlVersion.GL_11),
   INT(4, 5124, GlVersion.GL_11),
   HALF_FLOAT(2, 5131, GlVersion.GL_30),
   FLOAT(4, 5126, GlVersion.GL_11),
   UNSIGNED_BYTE(1, 5121, GlVersion.GL_11),
   UNSIGNED_BYTE_3_3_2(1, 32818, GlVersion.GL_12),
   UNSIGNED_BYTE_2_3_3_REV(1, 33634, GlVersion.GL_12),
   UNSIGNED_SHORT(2, 5123, GlVersion.GL_11),
   UNSIGNED_SHORT_5_6_5(2, 33635, GlVersion.GL_12),
   UNSIGNED_SHORT_5_6_5_REV(2, 33636, GlVersion.GL_12),
   UNSIGNED_SHORT_4_4_4_4(2, 32819, GlVersion.GL_12),
   UNSIGNED_SHORT_4_4_4_4_REV(2, 33637, GlVersion.GL_12),
   UNSIGNED_SHORT_5_5_5_1(2, 32820, GlVersion.GL_12),
   UNSIGNED_SHORT_1_5_5_5_REV(2, 33638, GlVersion.GL_12),
   UNSIGNED_INT(4, 5125, GlVersion.GL_11),
   UNSIGNED_INT_8_8_8_8(4, 32821, GlVersion.GL_12),
   UNSIGNED_INT_8_8_8_8_REV(4, 33639, GlVersion.GL_12),
   UNSIGNED_INT_10_10_10_2(4, 32822, GlVersion.GL_12),
   UNSIGNED_INT_2_10_10_10_REV(4, 33640, GlVersion.GL_12),
   UNSIGNED_INT_10F_11F_11F_REV(4, 35899, GlVersion.GL_30),
   UNSIGNED_INT_5_9_9_9_REV(4, 35902, GlVersion.GL_30);

   private final int byteSize;
   private final int glFormat;
   private final GlVersion minimumGlVersion;

   PixelType(int byteSize, int glFormat, GlVersion minimumGlVersion) {
      this.byteSize = byteSize;
      this.glFormat = glFormat;
      this.minimumGlVersion = minimumGlVersion;
   }

   public static Optional<PixelType> fromString(String name) {
      try {
         return Optional.of(valueOf(name.toUpperCase(Locale.US)));
      } catch (IllegalArgumentException e) {
         Iris.logger.error("Failed to find pixel type " + name.toUpperCase(Locale.ROOT));
         return Optional.empty();
      }
   }

   public int getByteSize() {
      return this.byteSize;
   }

   public int getGlFormat() {
      return this.glFormat;
   }

   public GlVersion getMinimumGlVersion() {
      return this.minimumGlVersion;
   }
}
