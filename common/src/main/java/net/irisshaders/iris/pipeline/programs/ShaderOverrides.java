package net.irisshaders.iris.pipeline.programs;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;

public class ShaderOverrides {
   public static ShaderKey getSkyShader(IrisRenderingPipeline pipeline) {
      return isSky(pipeline) ? ShaderKey.SKY_BASIC : ShaderKey.BASIC;
   }

   public static ShaderKey getSkyTexShader(IrisRenderingPipeline pipeline) {
      return isSky(pipeline) ? ShaderKey.SKY_TEXTURED : ShaderKey.TEXTURED;
   }

   public static ShaderKey getSkyTexColorShader(IrisRenderingPipeline pipeline) {
      return isSky(pipeline) ? ShaderKey.SKY_TEXTURED_COLOR : ShaderKey.TEXTURED_COLOR;
   }

   public static ShaderKey getSkyColorShader(IrisRenderingPipeline pipeline) {
      return isSky(pipeline) ? ShaderKey.SKY_BASIC_COLOR : ShaderKey.BASIC_COLOR;
   }

   public static boolean isBlockEntities(IrisRenderingPipeline pipeline) {
      return pipeline != null && pipeline.getPhase() == WorldRenderingPhase.BLOCK_ENTITIES;
   }

   public static boolean isEntities(IrisRenderingPipeline pipeline) {
      return pipeline != null && pipeline.getPhase() == WorldRenderingPhase.ENTITIES;
   }

   public static boolean isSky(IrisRenderingPipeline pipeline) {
      if (pipeline != null) {
         return switch (pipeline.getPhase()) {
            case CUSTOM_SKY, SKY, SUNSET, SUN, STARS, VOID, MOON -> true;
            default -> false;
         };
      } else {
         return false;
      }
   }

   public static boolean isPhase(IrisRenderingPipeline pipeline, WorldRenderingPhase phase) {
      return pipeline != null ? pipeline.getPhase() == phase : false;
   }
}
