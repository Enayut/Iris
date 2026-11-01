package net.irisshaders.iris.shaderpack;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import net.irisshaders.iris.Iris;

public class LanguageMap {
   private final Map<String, Map<String, String>> translationMaps = new HashMap<>();

   public LanguageMap(Path root) throws IOException {
      if (Files.exists(root)) {
         try (Stream<Path> stream = Files.list(root)) {
            stream.filter(path -> !Files.isDirectory(path)).forEach(path -> {
               String currentFileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
               if (currentFileName.endsWith(".lang")) {
                  String currentLangCode = currentFileName.substring(0, currentFileName.lastIndexOf("."));
                  Properties properties = new Properties();

                  try (InputStreamReader isr = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                     properties.load(isr);
                  } catch (IOException e) {
                     Iris.logger.error("Failed to parse shader pack language file " + path, e);
                  }

                  Builder<String, String> builder = ImmutableMap.builder();
                  properties.forEach((key, value) -> builder.put(key.toString(), value.toString()));
                  this.translationMaps.put(currentLangCode, builder.build());
               }
            });
         }
      }
   }

   public Set<String> getLanguages() {
      return Collections.unmodifiableSet(this.translationMaps.keySet());
   }

   public Map<String, String> getTranslations(String language) {
      return this.translationMaps.get(language);
   }
}
