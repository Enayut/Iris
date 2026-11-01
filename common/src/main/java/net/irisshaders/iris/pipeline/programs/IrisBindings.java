package net.irisshaders.iris.pipeline.programs;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL31C;

public final class IrisBindings {
   public static final int RESOURCE_COUNT = 32;
   public static final int ALBEDO_TEXTURE = 0;
   public static final int OVERLAY_TEXTURE = 1;
   public static final int LIGHTMAP_TEXTURE = 2;
   public static final int AUX_TEXTURE = 3;
   public static final int DYNAMIC_TRANSFORMS = 0;
   public static final int CLOUD_INFO = 1;
   public static final int PROJECTION = 2;
   public static final int GLOBALS = 3;
   public static final int FOG = 4;
   public static final int LIGHTING = 5;
   public static final int PUSH_CONSTANTS = 6;
   public static final int SODIUM_GLOBALS = 0;

   private IrisBindings() {
   }

   public static void apply(int program) {
      if (GL20C.glGetProgrami(program, 35714) != 0) {
         bindUniformBlocks(program, 0, "iris_DynamicTransforms", "DynamicTransforms", "TerrainUniform");
         bindUniformBlocks(program, 1, "iris_CloudInfo", "CloudInfo");
         bindUniformBlocks(program, 2, "iris_Projection", "Projection");
         bindUniformBlocks(program, 3, "iris_Globals", "Globals");
         bindUniformBlocks(program, 4, "iris_Fog", "Fog");
         bindUniformBlocks(program, 5, "iris_Lighting", "Lighting");
         bindUniformBlocks(program, 6, "iris_SodiumPushConstants");
         bindUniformBlocks(program, 0, "u_Globals");
         int previousProgram = GL20C.glGetInteger(35725);
         GlStateManager._glUseProgram(program);

         try {
            bindSamplers(program, 0, "tex", "texture", "gtexture", "u_MainSampler", "Sampler0", "u_BlockTex");
            bindSamplers(program, 1, "iris_overlay", "Sampler1");
            bindSamplers(program, 2, "lightmap", "Sampler2", "u_LightTex");
            bindSamplers(program, 3, "CloudFaces", "u_SectionTimeInfo");
         } finally {
            GlStateManager._glUseProgram(previousProgram);
         }
      }
   }

   private static void bindUniformBlocks(int program, int binding, String... names) {
      for (String name : names) {
         int index = GL31C.glGetUniformBlockIndex(program, name);
         if (index != -1) {
            GL31C.glUniformBlockBinding(program, index, binding);
         }
      }
   }

   private static void bindSamplers(int program, int binding, String... names) {
      for (String name : names) {
         int location = GlStateManager._glGetUniformLocation(program, name);
         if (location >= 0) {
            GlStateManager._glUniform1i(location, binding);
         }
      }
   }
}
