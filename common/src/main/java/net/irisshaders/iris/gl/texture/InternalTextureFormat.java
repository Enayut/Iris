package net.irisshaders.iris.gl.texture;

import java.util.Locale;
import java.util.Optional;
import net.irisshaders.iris.gl.GlVersion;

public enum InternalTextureFormat {
   RGBA(32856, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),
   R8(33321, GlVersion.GL_30, PixelFormat.RED, ShaderDataType.FLOAT),
   RG8(33323, GlVersion.GL_30, PixelFormat.RG, ShaderDataType.FLOAT),
   RGB8(32849, GlVersion.GL_11, PixelFormat.RGB, ShaderDataType.FLOAT),
   RGBA8(32856, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),
   R8_SNORM(36756, GlVersion.GL_31, PixelFormat.RED, ShaderDataType.FLOAT),
   RG8_SNORM(36757, GlVersion.GL_31, PixelFormat.RG, ShaderDataType.FLOAT),
   RGB8_SNORM(36758, GlVersion.GL_31, PixelFormat.RGB, ShaderDataType.FLOAT),
   RGBA8_SNORM(36759, GlVersion.GL_31, PixelFormat.RGBA, ShaderDataType.FLOAT),
   R16(33322, GlVersion.GL_30, PixelFormat.RED, ShaderDataType.FLOAT),
   RG16(33324, GlVersion.GL_30, PixelFormat.RG, ShaderDataType.FLOAT),
   RGB16(32852, GlVersion.GL_11, PixelFormat.RGB, ShaderDataType.FLOAT),
   RGBA16(32859, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),
   R16_SNORM(36760, GlVersion.GL_31, PixelFormat.RED, ShaderDataType.FLOAT),
   RG16_SNORM(36761, GlVersion.GL_31, PixelFormat.RG, ShaderDataType.FLOAT),
   RGB16_SNORM(36762, GlVersion.GL_31, PixelFormat.RGB, ShaderDataType.FLOAT),
   RGBA16_SNORM(36763, GlVersion.GL_31, PixelFormat.RGBA, ShaderDataType.FLOAT),
   R16F(33325, GlVersion.GL_30, PixelFormat.RED, ShaderDataType.FLOAT),
   RG16F(33327, GlVersion.GL_30, PixelFormat.RG, ShaderDataType.FLOAT),
   RGB16F(34843, GlVersion.GL_30, PixelFormat.RGB, ShaderDataType.FLOAT),
   RGBA16F(34842, GlVersion.GL_30, PixelFormat.RGBA, ShaderDataType.FLOAT),
   R32F(33326, GlVersion.GL_30, PixelFormat.RED, ShaderDataType.FLOAT),
   RG32F(33328, GlVersion.GL_30, PixelFormat.RG, ShaderDataType.FLOAT),
   RGB32F(34837, GlVersion.GL_30, PixelFormat.RGB, ShaderDataType.FLOAT),
   RGBA32F(34836, GlVersion.GL_30, PixelFormat.RGBA, ShaderDataType.FLOAT),
   R8I(33329, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.INT),
   RG8I(33335, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.INT),
   RGB8I(36239, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.INT),
   RGBA8I(36238, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.INT),
   R8UI(33330, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.UINT),
   RG8UI(33336, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.UINT),
   RGB8UI(36221, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.UINT),
   RGBA8UI(36220, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.UINT),
   R16I(33331, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.INT),
   RG16I(33337, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.INT),
   RGB16I(36233, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.INT),
   RGBA16I(36232, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.INT),
   R16UI(33332, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.UINT),
   RG16UI(33338, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.UINT),
   RGB16UI(36215, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.UINT),
   RGBA16UI(36214, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.UINT),
   R32I(33333, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.INT),
   RG32I(33339, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.INT),
   RGB32I(36227, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.INT),
   RGBA32I(36226, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.INT),
   R32UI(33334, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.UINT),
   RG32UI(33340, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.UINT),
   RGB32UI(36209, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.UINT),
   RGBA32UI(36208, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.UINT),
   RGBA2(32853, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),
   RGBA4(32854, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),
   R3_G3_B2(10768, GlVersion.GL_11, PixelFormat.RGB, ShaderDataType.FLOAT),
   RGB5_A1(32855, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),
   RGB565(36194, GlVersion.GL_41, PixelFormat.RGB, ShaderDataType.FLOAT),
   RGB10_A2(32857, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),
   RGB10_A2UI(36975, GlVersion.GL_33, PixelFormat.RGBA_INTEGER, ShaderDataType.UINT),
   R11F_G11F_B10F(35898, GlVersion.GL_30, PixelFormat.RGB, ShaderDataType.FLOAT),
   RGB9_E5(35901, GlVersion.GL_30, PixelFormat.RGB, ShaderDataType.FLOAT);

   private final int glFormat;
   private final GlVersion minimumGlVersion;
   private final PixelFormat expectedPixelFormat;
   private final ShaderDataType shaderDataType;

   InternalTextureFormat(int glFormat, GlVersion minimumGlVersion, PixelFormat expectedPixelFormat, ShaderDataType shaderDataType) {
      this.glFormat = glFormat;
      this.minimumGlVersion = minimumGlVersion;
      this.expectedPixelFormat = expectedPixelFormat;
      this.shaderDataType = shaderDataType;
   }

   public static Optional<InternalTextureFormat> fromString(String name) {
      try {
         return Optional.of(valueOf(name.toUpperCase(Locale.US)));
      } catch (IllegalArgumentException e) {
         return Optional.empty();
      }
   }

   public int getGlFormat() {
      return this.glFormat;
   }

   public PixelFormat getPixelFormat() {
      return this.expectedPixelFormat;
   }

   public GlVersion getMinimumGlVersion() {
      return this.minimumGlVersion;
   }

   public ShaderDataType getShaderDataType() {
      return this.shaderDataType;
   }
}
