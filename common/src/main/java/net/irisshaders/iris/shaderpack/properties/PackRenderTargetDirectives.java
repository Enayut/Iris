package net.irisshaders.iris.shaderpack.properties;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.shaderpack.parsing.DirectiveHolder;
import org.joml.Vector4f;

public class PackRenderTargetDirectives {
   public static final ImmutableList<String> LEGACY_RENDER_TARGETS = ImmutableList.of(
      "gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"
   );
   public static final Set<Integer> BASELINE_SUPPORTED_RENDER_TARGETS;
   private final Int2ObjectMap<PackRenderTargetDirectives.RenderTargetSettings> renderTargetSettings = new Int2ObjectOpenHashMap();

   PackRenderTargetDirectives(Set<Integer> supportedRenderTargets) {
      supportedRenderTargets.forEach(index -> this.renderTargetSettings.put(index, new PackRenderTargetDirectives.RenderTargetSettings()));
   }

   public IntList getBuffersToBeCleared() {
      IntList buffersToBeCleared = new IntArrayList();
      this.renderTargetSettings.forEach((index, settings) -> {
         if (settings.shouldClear()) {
            buffersToBeCleared.add(index);
         }
      });
      return buffersToBeCleared;
   }

   public Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> getRenderTargetSettings() {
      return Collections.unmodifiableMap(this.renderTargetSettings);
   }

   public void acceptDirectives(DirectiveHolder directives) {
      Optional.ofNullable((PackRenderTargetDirectives.RenderTargetSettings)this.renderTargetSettings.get(7))
         .ifPresent(
            colortex7 -> directives.acceptCommentStringDirective(
               "GAUX4FORMAT",
               format -> {
                  switch (format) {
                     case "RGBA32F":
                        colortex7.requestedFormat = InternalTextureFormat.RGBA32F;
                        break;
                     case "RGB32F":
                        colortex7.requestedFormat = InternalTextureFormat.RGB32F;
                        break;
                     case "RGB16":
                        colortex7.requestedFormat = InternalTextureFormat.RGB16;
                        break;
                     case null:
                     default:
                        Iris.logger
                           .warn(
                              "Ignoring GAUX4FORMAT directive /* GAUX4FORMAT:"
                                 + format
                                 + "*/ because "
                                 + format
                                 + " must be RGBA32F, RGB32F, or RGB16. Use `const int colortex7Format = "
                                 + format
                                 + ";` + instead."
                           );
                  }
               }
            )
         );
      Optional.ofNullable((PackRenderTargetDirectives.RenderTargetSettings)this.renderTargetSettings.get(1))
         .ifPresent(gdepth -> directives.acceptUniformDirective("gdepth", () -> {
            if (gdepth.requestedFormat == InternalTextureFormat.RGBA) {
               gdepth.requestedFormat = InternalTextureFormat.RGBA32F;
            }
         }));
      this.renderTargetSettings.forEach((index, settings) -> {
         this.acceptBufferDirectives(directives, settings, "colortex" + index);
         if (index < LEGACY_RENDER_TARGETS.size()) {
            this.acceptBufferDirectives(directives, settings, (String)LEGACY_RENDER_TARGETS.get(index));
         }
      });
   }

   private void acceptBufferDirectives(DirectiveHolder directives, PackRenderTargetDirectives.RenderTargetSettings settings, String bufferName) {
      directives.acceptConstStringDirective(bufferName + "Format", format -> {
         Optional<InternalTextureFormat> internalFormat = InternalTextureFormat.fromString(format);
         if (internalFormat.isPresent()) {
            settings.requestedFormat = internalFormat.get();
         } else {
            Iris.logger.warn("Unrecognized internal texture format " + format + " specified for " + bufferName + "Format, ignoring.");
         }
      });
      directives.acceptConstBooleanDirective(bufferName + "Clear", shouldClear -> settings.clear = shouldClear);
      directives.acceptConstVec4Directive(bufferName + "ClearColor", clearColor -> settings.clearColor = clearColor);
   }

   static {
      Builder<Integer> builder = ImmutableSet.builder();

      for (int i = 0; i < 32; i++) {
         builder.add(i);
      }

      BASELINE_SUPPORTED_RENDER_TARGETS = builder.build();
   }

   public static final class RenderTargetSettings {
      private InternalTextureFormat requestedFormat = InternalTextureFormat.RGBA;
      private boolean clear = true;
      private Vector4f clearColor = null;

      public InternalTextureFormat getInternalFormat() {
         return this.requestedFormat;
      }

      public boolean shouldClear() {
         return this.clear;
      }

      public Optional<Vector4f> getClearColor() {
         return Optional.ofNullable(this.clearColor);
      }

      @Override
      public String toString() {
         return "RenderTargetSettings{requestedFormat=" + this.requestedFormat + ", clear=" + this.clear + ", clearColor=" + this.clearColor + "}";
      }
   }
}
