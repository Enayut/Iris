package net.irisshaders.iris.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedBlockModelSubmit;
import net.irisshaders.iris.mixinterface.ModelStorage;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExtendedBlockModelSubmit.class)
public class MixinExtendedBlockModelSubmit implements ModelStorage {
   @Unique
   private int entityId;
   @Unique
   private int beId;
   @Unique
   private int itemId;
   @Unique
   private boolean isRenderingBEs;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void iris$c(
      Pose pose,
      Function renderTypeFunction,
      List modelParts,
      Mesh mesh,
      int[] tintLayers,
      int lightCoords,
      int overlayCoords,
      int tintColor,
      Pose sheetedDecalPose,
      CallbackInfo ci
   ) {
      this.iris$capture();
   }

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
