package net.irisshaders.iris.mixin;

import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LightningBoltRenderer.class)
public class MixinLightningBoltRenderer {
   private RenderType iris$overrideTex() {
      return null;
   }
}
