package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BannerRenderer.class)
public interface BannerRendererAccessor {
   @Invoker("submitPatternLayer")
   static <S> void iris$invokeSubmitPatternLayer(
      SpriteGetter sprites,
      PoseStack poseStack,
      OrderedSubmitNodeCollector submitNodeCollector,
      int lightCoords,
      int overlayCoords,
      Model<S> model,
      S state,
      SpriteId sprite,
      DyeColor color
   ) {
   }
}
