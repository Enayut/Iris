package net.irisshaders.iris.layer;

import java.util.function.Function;
import net.minecraft.client.renderer.rendertype.RenderType;

public interface WrappingMultiBufferSource {
   void pushWrappingFunction(Function<RenderType, RenderType> var1);

   void popWrappingFunction();

   void assertWrapStackEmpty();
}
