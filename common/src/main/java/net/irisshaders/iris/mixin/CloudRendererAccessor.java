package net.irisshaders.iris.mixin;

import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.CloudRenderer.TextureData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CloudRenderer.class)
public interface CloudRendererAccessor {
   @Accessor("texture")
   TextureData getTexture();
}
