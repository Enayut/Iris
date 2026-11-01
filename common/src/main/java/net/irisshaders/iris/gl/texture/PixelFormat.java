package net.irisshaders.iris.gl.texture;

import java.util.Locale;
import java.util.Optional;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.GlVersion;

public enum PixelFormat {
   RED(1, 6403, GlVersion.GL_11, false),
   RG(2, 33319, GlVersion.GL_30, false),
   RGB(3, 6407, GlVersion.GL_11, false),
   BGR(3, 32992, GlVersion.GL_12, false),
   RGBA(4, 6408, GlVersion.GL_11, false),
   BGRA(4, 32993, GlVersion.GL_12, false),
   RED_INTEGER(1, 36244, GlVersion.GL_30, true),
   RG_INTEGER(2, 33320, GlVersion.GL_30, true),
   RGB_INTEGER(3, 36248, GlVersion.GL_30, true),
   BGR_INTEGER(3, 36250, GlVersion.GL_30, true),
   RGBA_INTEGER(4, 36249, GlVersion.GL_30, true),
   BGRA_INTEGER(4, 36251, GlVersion.GL_30, true);

   private final int componentCount;
   private final int glFormat;
   private final GlVersion minimumGlVersion;
   private final boolean isInteger;

   PixelFormat(int componentCount, int glFormat, GlVersion minimumGlVersion, boolean isInteger) {
      this.componentCount = componentCount;
      this.glFormat = glFormat;
      this.minimumGlVersion = minimumGlVersion;
      this.isInteger = isInteger;
   }

   public static Optional<PixelFormat> fromString(String name) {
      try {
         return Optional.of(valueOf(name.toUpperCase(Locale.US)));
      } catch (IllegalArgumentException e) {
         Iris.logger.error("Looking for an illegal pixel format: " + name.toUpperCase(Locale.US));
         return Optional.empty();
      }
   }

   public int getComponentCount() {
      return this.componentCount;
   }

   public int getGlFormat() {
      return this.glFormat;
   }

   public GlVersion getMinimumGlVersion() {
      return this.minimumGlVersion;
   }

   public boolean isInteger() {
      return this.isInteger;
   }
}
