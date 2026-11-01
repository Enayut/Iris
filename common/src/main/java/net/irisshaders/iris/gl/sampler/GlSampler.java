package net.irisshaders.iris.gl.sampler;

import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;

public class GlSampler extends GlResource {
   public static final GlSampler MIPPED_LINEAR_HW = new GlSampler(true, true, true, true, true, true);
   public static final GlSampler LINEAR_HW = new GlSampler(true, true, false, true, true, true);
   public static final GlSampler MIPPED_NEAREST_HW = new GlSampler(false, true, true, true, true, true);
   public static final GlSampler NEAREST_HW = new GlSampler(false, true, false, true, true, true);
   public static final GlSampler MIPPED_LINEAR = new GlSampler(true, true, true, false, false, true);
   public static final GlSampler LINEAR = new GlSampler(true, true, false, false, false, true);
   public static final GlSampler MIPPED_NEAREST = new GlSampler(false, true, true, false, false, true);
   public static final GlSampler NEAREST = new GlSampler(false, true, false, false, false, true);
   public static final GlSampler NEAREST_REPEAT = new GlSampler(false, true, false, false, false, false);
   public static final GlSampler LINEAR_REPEAT = new GlSampler(true, true, false, false, false, false);
   public static final GlSampler MIPPED_NEAREST_NEAREST = new GlSampler(false, false, true, false, false, true);
   public static final GlSampler MIPPED_NEAREST_REPEAT = new GlSampler(false, true, true, false, false, false);

   public GlSampler(boolean linear, boolean linearMips, boolean mipmapped, boolean shadow, boolean hardwareShadow, boolean clamp) {
      super(IrisRenderSystem.genSampler());
      IrisRenderSystem.samplerParameteri(this.getId(), 10240, linear ? 9729 : 9728);
      IrisRenderSystem.samplerParameteri(this.getId(), 10242, clamp ? 33071 : 10497);
      IrisRenderSystem.samplerParameteri(this.getId(), 10243, clamp ? 33071 : 10497);
      if (mipmapped) {
         IrisRenderSystem.samplerParameteri(this.getId(), 10241, linearMips ? (linear ? 9987 : 9986) : (linear ? 9985 : 9984));
      } else {
         IrisRenderSystem.samplerParameteri(this.getId(), 10241, linear ? 9729 : 9728);
      }

      if (hardwareShadow) {
         IrisRenderSystem.samplerParameteri(this.getId(), 34892, 34894);
         IrisRenderSystem.samplerParameteri(this.getId(), 34893, 515);
      }
   }

   public GlSampler(int sampler) {
      super(sampler);
   }

   @Override
   protected void destroyInternal() {
      IrisRenderSystem.destroySampler(this.getGlId());
   }

   public int getId() {
      return this.getGlId();
   }
}
