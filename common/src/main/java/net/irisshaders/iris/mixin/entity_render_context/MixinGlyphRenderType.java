package net.irisshaders.iris.mixin.entity_render_context;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.irisshaders.iris.layer.BlockEntityRenderStateShard;
import net.irisshaders.iris.layer.OuterWrappedRenderType;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GlyphRenderTypes.class)
public class MixinGlyphRenderType {
   @WrapMethod(method = "select")
   private RenderType iris$select(DisplayMode displayMode, Operation<RenderType> original) {
      RenderType renderType = (RenderType)original.call(new Object[]{displayMode});
      if (ImmediateState.isRenderingBEs) {
         renderType = OuterWrappedRenderType.wrapExactlyOnce("iris:block_entity", renderType, BlockEntityRenderStateShard.INSTANCE);
      }

      return renderType;
   }
}
