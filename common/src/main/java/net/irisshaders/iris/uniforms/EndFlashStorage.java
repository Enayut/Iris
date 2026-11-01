package net.irisshaders.iris.uniforms;

import net.minecraft.client.Minecraft;

public class EndFlashStorage {
   private float lastEndFlash;
   private float currentEndFlash;

   public void tick() {
      this.lastEndFlash = this.currentEndFlash;
      this.currentEndFlash = Minecraft.getInstance().level.endFlashState() == null
         ? 0.0F
         : Minecraft.getInstance().level.endFlashState().getIntensity(CapturedRenderingState.INSTANCE.getTickDelta());
   }

   public float getLastEndFlash() {
      return this.lastEndFlash;
   }

   public float getCurrentEndFlash() {
      return this.currentEndFlash;
   }
}
