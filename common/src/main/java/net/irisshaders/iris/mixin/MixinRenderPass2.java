package net.irisshaders.iris.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.irisshaders.iris.mixinterface.RenderPassInterface;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderPass.class)
public interface MixinRenderPass2 extends RenderPassInterface {
}
