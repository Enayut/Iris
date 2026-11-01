package net.irisshaders.iris.mixin.entity_render_context;

import net.irisshaders.iris.mixinterface.ModelStorage;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Submit.class)
public class MixinModelSubmit implements ModelStorage {
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
}
