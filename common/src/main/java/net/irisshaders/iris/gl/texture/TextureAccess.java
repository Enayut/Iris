package net.irisshaders.iris.gl.texture;

import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.sampler.GlSampler;

public interface TextureAccess {
   TextureType getType();

   IntSupplier getTextureId();

   GlSampler getSampling();
}
