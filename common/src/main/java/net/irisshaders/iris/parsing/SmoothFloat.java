package net.irisshaders.iris.parsing;

import net.irisshaders.iris.uniforms.SystemTimeUniforms;

public class SmoothFloat {
   private static final double LN_OF_2 = Math.log(2.0);
   private float accumulator;
   private boolean hasInitialValue;
   private float cachedHalfLifeUp;
   private float cachedDecayUp;
   private float cachedHalfLifeDown;
   private float cachedDecayDown;

   private static float exponentialDecayFactor(float k, float t) {
      return (float)Math.exp(-k * t);
   }

   private static float lerp(float v0, float v1, float t) {
      return (1.0F - t) * v0 + t * v1;
   }

   public float updateAndGet(float value, float halfLifeUp, float halfLifeDown) {
      if (halfLifeUp != this.cachedHalfLifeUp) {
         this.cachedHalfLifeUp = halfLifeUp;
         if (halfLifeUp == 0.0F) {
            this.cachedDecayUp = 0.0F;
         } else {
            this.cachedDecayUp = this.computeDecay(halfLifeUp * 0.1F);
         }
      }

      if (halfLifeDown != this.cachedHalfLifeDown) {
         this.cachedHalfLifeDown = halfLifeDown;
         if (halfLifeDown == 0.0F) {
            this.cachedDecayDown = 0.0F;
         } else {
            this.cachedDecayDown = this.computeDecay(halfLifeDown * 0.1F);
         }
      }

      if (!this.hasInitialValue) {
         this.accumulator = value;
         this.hasInitialValue = true;
         return this.accumulator;
      } else {
         float lastFrameTime = SystemTimeUniforms.TIMER.getLastFrameTime();
         float decay = value > this.accumulator ? this.cachedDecayUp : this.cachedDecayDown;
         if (decay == 0.0F) {
            this.accumulator = value;
            return this.accumulator;
         } else {
            float smoothingFactor = 1.0F - exponentialDecayFactor(decay, lastFrameTime);
            this.accumulator = lerp(this.accumulator, value, smoothingFactor);
            return this.accumulator;
         }
      }
   }

   private float computeDecay(float halfLife) {
      return (float)(1.0 / (halfLife / LN_OF_2));
   }
}
