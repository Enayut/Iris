package net.irisshaders.iris.gl.texture;

import com.mojang.renderpearl.api.GpuFormat;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

public enum DepthBufferFormat {
   DEPTH16(false),
   DEPTH32F(false),
   DEPTH24_STENCIL8(true),
   DEPTH32F_STENCIL8(true);

   private final boolean combinedStencil;

   DepthBufferFormat(boolean combinedStencil) {
      this.combinedStencil = combinedStencil;
   }

   @Nullable
   public static DepthBufferFormat fromGlEnum(int glenum) {
      return switch (glenum) {
         case 33189 -> DEPTH16;
         case 35056 -> DEPTH24_STENCIL8;
         case 36012 -> DEPTH32F;
         case 36013 -> DEPTH32F_STENCIL8;
         default -> null;
      };
   }

   public static DepthBufferFormat fromGlEnumOrDefault(int glenum) {
      DepthBufferFormat format = fromGlEnum(glenum);
      return Objects.requireNonNullElse(format, DEPTH32F);
   }

   public int getGlInternalFormat() {
      return switch (this) {
         case DEPTH16 -> 33189;
         case DEPTH32F -> 36012;
         case DEPTH24_STENCIL8 -> 35056;
         case DEPTH32F_STENCIL8 -> 36013;
      };
   }

   public int getGlType() {
      return this.isCombinedStencil() ? 34041 : 6402;
   }

   public int getGlFormat() {
      return switch (this) {
         case DEPTH16 -> 5123;
         case DEPTH32F -> 5126;
         case DEPTH24_STENCIL8 -> 34042;
         case DEPTH32F_STENCIL8 -> 36269;
      };
   }

   public boolean isCombinedStencil() {
      return this.combinedStencil;
   }

   public GpuFormat toMojang() {
      return switch (this) {
         case DEPTH16 -> GpuFormat.D16_UNORM;
         case DEPTH32F -> GpuFormat.D32_FLOAT;
         case DEPTH24_STENCIL8 -> GpuFormat.D24_UNORM_S8_UINT;
         case DEPTH32F_STENCIL8 -> GpuFormat.D32_FLOAT_S8_UINT;
      };
   }
}
