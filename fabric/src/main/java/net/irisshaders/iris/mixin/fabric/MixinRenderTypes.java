package net.irisshaders.iris.mixin.fabric;

import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Matches upstream Iris 26.3: the 26.3 weather rendering path made this
 * redirect unnecessary, but the (empty, annotated) mixin is retained for
 * package/config stability. It must keep its {@code @Mixin} annotation or
 * the mixin processor rejects it during PREPARE.
 */
@Mixin(WeatherEffectRenderer.class)
public class MixinRenderTypes {
}
