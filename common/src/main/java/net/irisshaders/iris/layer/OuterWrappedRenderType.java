package net.irisshaders.iris.layer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;

public class OuterWrappedRenderType extends RenderType {
   private static final RenderSetup FAKE_SETUP = RenderSetup.builder(RenderPipelines.GUI_TEXTURED).createRenderSetup();
   private final RenderingWrapper extra;
   private final RenderType wrapped;

   public OuterWrappedRenderType(String name, RenderType wrapped, RenderingWrapper extra) {
      super(name, FAKE_SETUP);
      this.extra = extra;
      this.wrapped = wrapped;
   }

   public static OuterWrappedRenderType wrapExactlyOnce(String name, RenderType wrapped, RenderingWrapper extra) {
      while (wrapped instanceof OuterWrappedRenderType) {
         wrapped = ((OuterWrappedRenderType)wrapped).unwrap();
      }

      return new OuterWrappedRenderType(name, wrapped, extra);
   }

   private RenderType unwrap() {
      return this.wrapped;
   }

   public boolean hasBlending() {
      return this.wrapped.hasBlending();
   }

   public boolean forceSolidModelPhase() {
      return this.wrapped.forceSolidModelPhase();
   }

   public Optional<RenderType> outline() {
      return this.wrapped.outline();
   }

   public boolean isOutline() {
      return this.wrapped.isOutline();
   }

   public RenderPipeline pipeline() {
      return this.wrapped.pipeline();
   }

   public PreparedRenderType prepare() {
      PreparedRenderType x = this.wrapped.prepare();
      ((WrappedPreparedRenderType)(Object)x).setRenderWrapper(this.extra);
      return x;
   }

   public boolean sortOnUpload() {
      return this.wrapped.sortOnUpload();
   }

   public RenderPipeline iris$getPipeline() {
      return this.wrapped.iris$getPipeline();
   }

   public RenderTarget iris$getRenderTarget() {
      return this.wrapped.iris$getRenderTarget();
   }

   public boolean canConsolidateConsecutiveGeometry() {
      return this.wrapped.canConsolidateConsecutiveGeometry();
   }

   public boolean affectsCrumbling() {
      return this.wrapped.affectsCrumbling();
   }

   public VertexFormat format() {
      return this.wrapped.format();
   }

   public PrimitiveTopology primitiveTopology() {
      return this.wrapped.primitiveTopology();
   }

   public boolean equals(@Nullable Object object) {
      if (object == null) {
         return false;
      }

      if (object.getClass() != this.getClass()) {
         return false;
      }

      OuterWrappedRenderType other = (OuterWrappedRenderType)object;
      return Objects.equals(this.wrapped, other.wrapped) && Objects.equals(this.extra, other.extra);
   }

   public int hashCode() {
      return this.wrapped.hashCode() + 1;
   }

   public String toString() {
      return "iris_wrapped:" + this.wrapped.toString();
   }
}
