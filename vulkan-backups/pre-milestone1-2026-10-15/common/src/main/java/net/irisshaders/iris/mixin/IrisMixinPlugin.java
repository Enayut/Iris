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

   public void onLoad(String mixinPackage) {
   }

   public String getRefMapperConfig() {
      return "iris.refmap.json";
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      return mixinClassName.contains("VKOnly") ? usingVulkan : !usingVulkan;
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
