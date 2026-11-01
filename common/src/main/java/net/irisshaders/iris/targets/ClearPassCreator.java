package net.irisshaders.iris.targets;

import com.google.common.collect.ImmutableList;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shaderpack.properties.PackShadowDirectives;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import org.joml.Vector2i;
import org.joml.Vector4f;

public class ClearPassCreator {
   public static ImmutableList<ClearPass> createClearPasses(RenderTargets renderTargets, boolean fullClear, PackRenderTargetDirectives renderTargetDirectives) {
      int maxDrawBuffers = GlStateManager._getInteger(34852);
      Map<Vector2i, Map<ClearPassInformation, IntList>> clearByColor = new HashMap<>();
      renderTargetDirectives.getRenderTargetSettings()
         .forEach(
            (bufferI, settings) -> {
               int buffer = bufferI;
               if (fullClear || settings.shouldClear()) {
                  Vector4f defaultClearColor;
                  if (buffer == 0) {
                     defaultClearColor = null;
                  } else if (buffer == 1) {
                     defaultClearColor = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
                  } else {
                     defaultClearColor = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
                  }

                  RenderTarget target = renderTargets.get(buffer);
                  if (target == null) {
                     return;
                  }

                  Vector4f clearColor = settings.getClearColor().orElse(defaultClearColor);
                  clearByColor.computeIfAbsent(new Vector2i(target.getWidth(), target.getHeight()), size -> new HashMap<>())
                     .computeIfAbsent(new ClearPassInformation(clearColor, target.getWidth(), target.getHeight()), color -> new IntArrayList())
                     .add(buffer);
               }
            }
         );
      List<ClearPass> clearPasses = new ArrayList<>();
      clearByColor.forEach(
         (passSize, vector4fIntListMap) -> vector4fIntListMap.forEach(
            (clearInfo, buffers) -> {
               int startIndex = 0;

               while (startIndex < buffers.size()) {
                  int[] clearBuffers = new int[Math.min(buffers.size() - startIndex, maxDrawBuffers)];

                  for (int i = 0; i < clearBuffers.length; i++) {
                     clearBuffers[i] = buffers.getInt(startIndex);
                     startIndex++;
                  }

                  clearPasses.add(
                     new ClearPass(
                        clearInfo.getColor(), clearInfo::getWidth, clearInfo::getHeight, renderTargets.createClearFramebuffer(true, clearBuffers), 16384
                     )
                  );
                  clearPasses.add(
                     new ClearPass(
                        clearInfo.getColor(), clearInfo::getWidth, clearInfo::getHeight, renderTargets.createClearFramebuffer(false, clearBuffers), 16384
                     )
                  );
               }
            }
         )
      );
      return ImmutableList.copyOf(clearPasses);
   }

   public static ImmutableList<ClearPass> createShadowClearPasses(
      ShadowRenderTargets renderTargets, boolean fullClear, PackShadowDirectives renderTargetDirectives
   ) {
      if (renderTargets == null) {
         return ImmutableList.of();
      }

      int maxDrawBuffers = GlStateManager._getInteger(34852);
      Map<Vector4f, IntList> clearByColor = new HashMap<>();

      for (int i = 0; i < renderTargets.getRenderTargetCount(); i++) {
         if (renderTargets.get(i) != null) {
            PackShadowDirectives.SamplingSettings settings = (PackShadowDirectives.SamplingSettings)renderTargetDirectives.getColorSamplingSettings().get(i);
            if (fullClear || settings.getClear()) {
               Vector4f clearColor = settings.getClearColor();
               clearByColor.computeIfAbsent(clearColor, color -> new IntArrayList()).add(i);
            }
         }
      }

      List<ClearPass> clearPasses = new ArrayList<>();
      clearByColor.forEach(
         (clearColorx, buffers) -> {
            int startIndex = 0;

            while (startIndex < buffers.size()) {
               int[] clearBuffers = new int[Math.min(buffers.size() - startIndex, maxDrawBuffers)];

               for (int i = 0; i < clearBuffers.length; i++) {
                  clearBuffers[i] = buffers.getInt(startIndex);
                  startIndex++;
               }

               clearPasses.add(
                  new ClearPass(
                     clearColorx, renderTargets::getResolution, renderTargets::getResolution, renderTargets.createFramebufferWritingToAlt(clearBuffers), 16384
                  )
               );
               clearPasses.add(
                  new ClearPass(
                     clearColorx, renderTargets::getResolution, renderTargets::getResolution, renderTargets.createFramebufferWritingToMain(clearBuffers), 16384
                  )
               );
            }
         }
      );
      return ImmutableList.copyOf(clearPasses);
   }
}
