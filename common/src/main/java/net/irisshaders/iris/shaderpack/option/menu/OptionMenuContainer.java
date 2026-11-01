package net.irisshaders.iris.shaderpack.option.menu;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.option.ProfileSet;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;

public class OptionMenuContainer {
   public final OptionMenuElementScreen mainScreen;
   public final Map<String, OptionMenuElementScreen> subScreens = new HashMap<>();
   private final List<OptionMenuOptionElement> usedOptionElements = new ArrayList<>();
   private final List<String> usedOptions = new ArrayList<>();
   private final List<String> unusedOptions = new ArrayList<>();
   private final Map<List<OptionMenuElement>, Integer> unusedOptionDumpQueue = new HashMap<>();
   private final ProfileSet profiles;

   public OptionMenuContainer(ShaderProperties shaderProperties, ShaderPackOptions shaderPackOptions, ProfileSet profiles) {
      this.profiles = profiles;
      this.mainScreen = new OptionMenuMainElementScreen(
         this,
         shaderProperties,
         shaderPackOptions,
         shaderProperties.getMainScreenOptions().orElseGet(() -> Collections.singletonList("*")),
         shaderProperties.getMainScreenColumnCount()
      );
      this.unusedOptions.addAll(shaderPackOptions.getOptionSet().getBooleanOptions().keySet());
      this.unusedOptions.addAll(shaderPackOptions.getOptionSet().getStringOptions().keySet());
      Map<String, Integer> subScreenColumnCounts = shaderProperties.getSubScreenColumnCount();
      shaderProperties.getSubScreenOptions()
         .forEach(
            (screenKey, options) -> this.subScreens
               .put(
                  screenKey,
                  new OptionMenuSubElementScreen(
                     screenKey, this, shaderProperties, shaderPackOptions, (List<String>)options, Optional.ofNullable(subScreenColumnCounts.get(screenKey))
                  )
               )
         );

      for (Entry<List<OptionMenuElement>, Integer> entry : this.unusedOptionDumpQueue.entrySet()) {
         List<OptionMenuElement> elementsToInsert = new ArrayList<>();

         for (String optionId : Lists.newArrayList(this.unusedOptions)) {
            try {
               OptionMenuElement element = OptionMenuElement.create(optionId, this, shaderProperties, shaderPackOptions);
               if (element != null) {
                  elementsToInsert.add(element);
                  if (element instanceof OptionMenuOptionElement) {
                     this.notifyOptionAdded(optionId, (OptionMenuOptionElement)element);
                  }
               }
            } catch (IllegalArgumentException error) {
               Iris.logger.warn(error);
               elementsToInsert.add(OptionMenuElement.EMPTY);
            }
         }

         entry.getKey().addAll(entry.getValue(), elementsToInsert);
      }
   }

   public ProfileSet getProfiles() {
      return this.profiles;
   }

   public void queueForUnusedOptionDump(int index, List<OptionMenuElement> elementList) {
      this.unusedOptionDumpQueue.put(elementList, index);
   }

   public void notifyOptionAdded(String optionId, OptionMenuOptionElement option) {
      if (!this.usedOptions.contains(optionId)) {
         this.usedOptionElements.add(option);
         this.usedOptions.add(optionId);
      }

      this.unusedOptions.remove(optionId);
   }
}
