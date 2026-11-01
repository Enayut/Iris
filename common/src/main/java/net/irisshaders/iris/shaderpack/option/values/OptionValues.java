package net.irisshaders.iris.shaderpack.option.values;

import java.util.Optional;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.helpers.OptionalBoolean;
import net.irisshaders.iris.shaderpack.option.MergedBooleanOption;
import net.irisshaders.iris.shaderpack.option.MergedStringOption;
import net.irisshaders.iris.shaderpack.option.OptionSet;

public interface OptionValues {
   OptionalBoolean getBooleanValue(String var1);

   Optional<String> getStringValue(String var1);

   default boolean getBooleanValueOrDefault(String name) {
      if ("0".equals(name)) {
         return false;
      } else {
         return "1".equals(name) ? true : this.getBooleanValue(name).orElseGet(() -> {
            if (!this.getOptionSet().getBooleanOptions().containsKey(name)) {
               Iris.logger.warn("Tried to get boolean value for unknown option: " + name + ", defaulting to true!");
               return true;
            } else {
               return ((MergedBooleanOption)this.getOptionSet().getBooleanOptions().get(name)).getOption().getDefaultValue();
            }
         });
      }
   }

   default String getStringValueOrDefault(String name) {
      return this.getStringValue(name).orElseGet(() -> ((MergedStringOption)this.getOptionSet().getStringOptions().get(name)).getOption().getDefaultValue());
   }

   int getOptionsChanged();

   MutableOptionValues mutableCopy();

   ImmutableOptionValues toImmutable();

   OptionSet getOptionSet();
}
