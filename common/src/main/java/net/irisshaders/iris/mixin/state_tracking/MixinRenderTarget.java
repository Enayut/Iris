package net.irisshaders.iris.mixin.state_tracking;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public class MixinRenderTarget {
   private void iris$onBindFramebuffer(boolean bl, CallbackInfo ci) {
      boolean mainBound = ((Object) this) == Minecraft.getInstance().gameRenderer.mainRenderTarget();
      Iris.getPipelineManager().getPipeline().ifPresent(pipeline -> pipeline.setIsMainBound(mainBound));
   }
}
