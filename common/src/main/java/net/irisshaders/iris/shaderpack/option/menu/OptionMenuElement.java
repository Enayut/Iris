package net.irisshaders.iris.shaderpack.option.menu;

import java.util.Map;
import net.irisshaders.iris.shaderpack.option.MergedBooleanOption;
import net.irisshaders.iris.shaderpack.option.MergedStringOption;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;

public abstract class OptionMenuElement {
   public static final OptionMenuElement EMPTY = new OptionMenuElement() {};
   private static final String ELEMENT_EMPTY = "<empty>";
   private static final String ELEMENT_PROFILE = "<profile>";

   public static OptionMenuElement create(
      String elementString, OptionMenuContainer container, ShaderProperties shaderProperties, ShaderPackOptions shaderPackOptions
   ) throws IllegalArgumentException {
      if ("<empty>".equals(elementString)) {
         return EMPTY;
      } else if ("<profile>".equals(elementString)) {
         return container.getProfiles().size() > 0
            ? new OptionMenuProfileElement(container.getProfiles(), shaderPackOptions.getOptionSet(), shaderPackOptions.getOptionValues())
            : null;
      } else if (elementString.startsWith("[") && elementString.endsWith("]")) {
         return new OptionMenuLinkElement(elementString.substring(1, elementString.length() - 1));
      } else {
         Map<String, MergedBooleanOption> booleanOptions = shaderPackOptions.getOptionSet().getBooleanOptions();
         Map<String, MergedStringOption> stringOptions = shaderPackOptions.getOptionSet().getStringOptions();
         if (booleanOptions.containsKey(elementString)) {
            return new OptionMenuBooleanOptionElement(
               elementString, container, shaderProperties, shaderPackOptions.getOptionValues(), booleanOptions.get(elementString).getOption()
            );
         } else if (stringOptions.containsKey(elementString)) {
            return new OptionMenuStringOptionElement(
               elementString, container, shaderProperties, shaderPackOptions.getOptionValues(), stringOptions.get(elementString).getOption()
            );
         } else {
            throw new IllegalArgumentException("Unable to resolve shader pack option menu element \"" + elementString + "\" defined in shaders.properties");
         }
      }
   }
}
