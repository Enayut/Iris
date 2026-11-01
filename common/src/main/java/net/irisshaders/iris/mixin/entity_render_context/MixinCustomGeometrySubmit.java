package net.irisshaders.iris.mixin.entity_render_context;

import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.irisshaders.iris.mixinterface.ModelStorage;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer.Submit;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Submit.class)
public class MixinCustomGeometrySubmit implements ModelStorage {
   @Unique
   private int entityId;
   @Unique
   private int beId;
   @Unique
   private int itemId;
   @Unique
   private boolean isRenderingBEs;

   @Override
   public void iris$capture() {
      this.entityId = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
      this.beId = CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
      this.itemId = CapturedRenderingState.INSTANCE.getCurrentRenderedItem();
      this.isRenderingBEs = ImmediateState.isRenderingBEs;
   }

   @Override
   public void iris$set() {
      CapturedRenderingState.INSTANCE.setCurrentEntity(this.entityId);
      CapturedRenderingState.INSTANCE.setCurrentBlockEntity(this.beId);
      CapturedRenderingState.INSTANCE.setCurrentRenderedItem(this.itemId);
   }

   @Override
   public boolean iris$wasBE() {
      return this.isRenderingBEs;
   }

   @Inject(method = "<init>", at = @At("RETURN"))
   private void iris$capture2(Pose pose, RenderType renderType, CustomGeometryRenderer customGeometryRenderer, CallbackInfo ci) {
      this.iris$capture();
   }
}
