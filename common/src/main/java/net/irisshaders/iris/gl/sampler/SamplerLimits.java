package net.irisshaders.iris.gl.sampler;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.irisshaders.iris.gl.IrisRenderSystem;

public class SamplerLimits {
   private static SamplerLimits instance;
   private final int maxTextureUnits = GlStateManager._getInteger(34930);
   private final int maxDrawBuffers = GlStateManager._getInteger(34852);
   private final int maxShaderStorageUnits = IrisRenderSystem.supportsSSBO() ? GlStateManager._getInteger(37085) : 0;

   private SamplerLimits() {
   }

   public static SamplerLimits get() {
      if (instance == null) {
         instance = new SamplerLimits();
      }

      return instance;
   }

   public int getMaxTextureUnits() {
      return this.maxTextureUnits;
   }

   public int getMaxDrawBuffers() {
      return this.maxDrawBuffers;
   }

   public int getMaxShaderStorageUnits() {
      return this.maxShaderStorageUnits;
   }
}
