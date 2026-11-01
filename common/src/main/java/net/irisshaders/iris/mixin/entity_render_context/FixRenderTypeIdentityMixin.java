package net.irisshaders.iris.mixin.entity_render_context;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Objects;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer$Group")
public class FixRenderTypeIdentityMixin {
   @Shadow
   private @Nullable RenderType lastRenderType;

   @ModifyExpressionValue(method = "getVertexBuilder", at = @At("MIXINEXTRAS:EXPRESSION"))
   @Definition(
      id = "lastRenderType",
      field = "Lnet/minecraft/client/renderer/feature/RenderTypeFeatureRenderer$Group;lastRenderType:Lnet/minecraft/client/renderer/rendertype/RenderType;"
   )
   @Expression("this.lastRenderType != ?")
   private boolean iris$replaceEqualityCheck(boolean original, @Local(argsOnly = true) RenderType renderType) {
      return this.lastRenderType == null
         ? true
         : !this.lastRenderType.getClass().equals(renderType.getClass()) || !Objects.equals(this.lastRenderType, renderType);
   }
}
