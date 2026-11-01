package net.irisshaders.iris.mixin.vertices;

import com.google.common.collect.ImmutableSet;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.List;
import net.irisshaders.iris.pipeline.programs.VertexFormatExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VertexFormat.class)
public abstract class MixinVertexFormat implements VertexFormatExtension {
   @Unique
   private static final ImmutableSet<String> ATTRIBUTE_LIST = ImmutableSet.of(
      "Position", "Color", "Normal", "UV0", "UV1", "UV2", new String[]{"UV3", "LineWidth"}
   );

   @Shadow
   public abstract List<VertexFormatElement> getElements();

   @Override
   public void bindAttributesIris(boolean isFallback, int i) {
      int j = 0;

      for (VertexFormatElement x : this.getElements()) {
         String string = x.name();
         GlStateManager._glBindAttribLocation(i, j, ATTRIBUTE_LIST.contains(string) && !isFallback ? "iris_" + string : string);
         j++;
      }
   }
}
