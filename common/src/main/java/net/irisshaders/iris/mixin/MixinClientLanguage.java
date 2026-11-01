package net.irisshaders.iris.mixin;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.LanguageMap;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientLanguage.class, priority = 990)
public class MixinClientLanguage {
   @Unique
   private static final String LOAD = "Lnet/minecraft/client/resources/language/ClientLanguage;loadFrom(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;)Lnet/minecraft/client/resources/language/ClientLanguage;";
   @Unique
   private static final List<String> languageCodes = new ArrayList<>();
   @Shadow
   @Final
   private Map<String, String> storage;

   @Inject(method = "appendFrom", at = @At("HEAD"))
   private static void injectFrom(String string, List<Resource> list, Map<String, String> map, CallbackInfo ci) {
      String json = String.format(Locale.ROOT, "lang/%s.json", string);
      if (Iris.class.getResource("/assets/iris/" + json) != null) {
         Language.loadFromJson(Iris.class.getResourceAsStream("/assets/iris/" + json), map::put);
      }
   }

   @Inject(method = "loadFrom", at = @At("HEAD"))
   private static void check(ResourceManager resourceManager, List<String> definitions, boolean bl, CallbackInfoReturnable<ClientLanguage> cir) {
      languageCodes.clear();
      new LinkedList<>(definitions).descendingIterator().forEachRemaining(languageCodes::add);
   }

   @Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
   private void iris$addLanguageEntries(String key, String value, CallbackInfoReturnable<String> cir) {
      String override = this.iris$lookupOverriddenEntry(key);
      if (override != null) {
         cir.setReturnValue(override);
      }
   }

   @Inject(method = "has", at = @At("HEAD"), cancellable = true)
   private void iris$addLanguageEntriesToTranslationChecks(String key, CallbackInfoReturnable<Boolean> cir) {
      String override = this.iris$lookupOverriddenEntry(key);
      if (override != null) {
         cir.setReturnValue(true);
      }
   }

   @Unique
   private String iris$lookupOverriddenEntry(String key) {
      ShaderPack pack = Iris.getCurrentPack().orElse(null);
      if (pack == null) {
         return null;
      }

      LanguageMap languageMap = pack.getLanguageMap();
      if (this.storage.containsKey(key)) {
         return null;
      }

      for (String code : languageCodes) {
         Map<String, String> translations = languageMap.getTranslations(code);
         if (translations != null) {
            String translation = translations.get(key);
            if (translation != null) {
               return translation;
            }
         }
      }

      return null;
   }
}
