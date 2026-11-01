package net.irisshaders.iris.mixin;

import com.google.common.base.Splitter;
import com.google.common.io.Files;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class IrisMixinPlugin implements IMixinConfigPlugin {
   private static final Splitter OPTION_SPLITTER = Splitter.on(':').limit(2);
   public static boolean usingVulkan;
   /**
    * Mixins applied on both backends because code shared by the OpenGL and Vulkan paths casts to the interface they
    * add. They must not touch the OpenGL backend; vulkanShaderCheck (VulkanMixinCoverageCheck) verifies both.
    */
   public static final Set<String> BACKEND_NEUTRAL_MIXINS = Set.of(
      // IrisItemLightProvider on every Item: held-item light uniforms (IdMapUniforms.HeldItemSupplier).
      "net.irisshaders.iris.mixin.MixinItem",
      // LocalPlayerInterface: the constantMood uniform; it reads BiomeAmbienceInterface from the next mixin.
      "net.irisshaders.iris.mixin.MixinLocalPlayer",
      // BiomeAmbienceInterface: tracks constant moodiness in vanilla's ambient sound tick.
      "net.irisshaders.iris.mixin.MixinBiomeAmbientSoundsHandler",
      // ExtendedBiome: the biome_category and rainfall uniforms (BiomeUniforms).
      "net.irisshaders.iris.mixin.MixinBiome",
      // Sodium chunk meshing in Iris's terrain vertex format (block IDs, normals, mid-texture and mid-block data),
      // which the pack's gbuffers_terrain programs read on Vulkan too. CPU-side meshing only.
      "net.irisshaders.iris.compat.sodium.mixin.MixinChunkMeshFormats",
      "net.irisshaders.iris.compat.sodium.mixin.MixinRenderSectionManager",
      "net.irisshaders.iris.compat.sodium.mixin.MixinChunkMeshBuildTask",
      "net.irisshaders.iris.compat.sodium.mixin.MixinBlockRenderer",
      "net.irisshaders.iris.compat.sodium.mixin.MixinAbstractBlockRenderContext",
      "net.irisshaders.iris.compat.sodium.mixin.MixinDefaultFluidRenderer",
      "net.irisshaders.iris.compat.sodium.mixin.MixinChunkVertex",
      // Directional shading off for packs with their own lighting (WorldRenderingSettings), as on OpenGL.
      "net.irisshaders.iris.mixin.vertices.block_rendering.MixinClientLevel"
   );

   public void onLoad(String mixinPackage) {
   }

   public String getRefMapperConfig() {
      return "iris.refmap.json";
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      if (mixinClassName.contains("VKOnly")) {
         return usingVulkan;
      }

      // @Accessor/@Invoker interfaces add no behaviour, so shared code (e.g. uniform suppliers) may rely on them on either backend.
      if (mixinClassName.endsWith("Accessor") || BACKEND_NEUTRAL_MIXINS.contains(mixinClassName)) {
         return true;
      }

      return !usingVulkan;
   }

   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   public List<String> getMixins() {
      return List.of();
   }

   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   static {
      BufferedReader reader = null;
      boolean check = true;

      try {
         reader = Files.newReader(IrisPlatformHelpers.getInstance().getGameDir().resolve("options.txt").toFile(), StandardCharsets.UTF_8);
      } catch (FileNotFoundException e) {
         usingVulkan = false;
         check = false;
      }

      if (check) {
         Map<String, String> options = new HashMap<>();

         try {
            reader.lines().forEach(line -> {
               try {
                  Iterator<String> iterator = OPTION_SPLITTER.split(line).iterator();
                  options.put(iterator.next(), iterator.next());
               } catch (Exception var3) {
               }
            });
         } catch (Throwable var6) {
            if (reader != null) {
               try {
                  reader.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (options.get("preferredGraphicsBackend") != null) {
            usingVulkan = options.get("preferredGraphicsBackend").toLowerCase(Locale.ROOT).contains("vulkan");
         } else {
            usingVulkan = false;
         }
      }
   }
}
