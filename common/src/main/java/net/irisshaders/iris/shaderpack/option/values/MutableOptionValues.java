package net.irisshaders.iris.shaderpack.option.values;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.irisshaders.iris.helpers.OptionalBoolean;
import net.irisshaders.iris.shaderpack.option.OptionSet;

public class MutableOptionValues implements OptionValues {
   private final OptionSet options;
   private final Map<String, Boolean> booleanValues;
   private final Map<String, String> stringValues;

   MutableOptionValues(OptionSet options, Map<String, Boolean> booleanValues, Map<String, String> stringValues) {
      Map<String, String> values = new HashMap<>();
      booleanValues.forEach((k, v) -> values.put(k, Boolean.toString(v)));
      values.putAll(stringValues);
      this.options = options;
      this.booleanValues = new HashMap<>();
      this.stringValues = new HashMap<>();
      this.addAll(values);
   }

   public MutableOptionValues(OptionSet options, Map<String, String> values) {
      this.options = options;
      this.booleanValues = new HashMap<>();
      this.stringValues = new HashMap<>();
      this.addAll(values);
   }

   public OptionSet getOptions() {
      return this.options;
   }

   public Map<String, Boolean> getBooleanValues() {
      return this.booleanValues;
   }

   public Map<String, String> getStringValues() {
      return this.stringValues;
   }

   public void addAll(Map<String, String> values) {
      this.options.getBooleanOptions().forEach((name, option) -> {
         String value = values.get(name);
         if (value != null) {
            OptionalBoolean booleanValue;
            if (value.equals("false")) {
               booleanValue = OptionalBoolean.FALSE;
            } else if (value.equals("true")) {
               booleanValue = OptionalBoolean.TRUE;
            } else {
               booleanValue = OptionalBoolean.DEFAULT;
            }

            boolean actualValue = booleanValue.orElse(option.getOption().getDefaultValue());
            if (actualValue == option.getOption().getDefaultValue()) {
               this.booleanValues.remove(name);
            } else {
               this.booleanValues.put(name, actualValue);
            }
         }
      });
      this.options.getStringOptions().forEach((name, option) -> {
         String value = values.get(name);
         if (value != null) {
            if (value.equals(option.getOption().getDefaultValue())) {
               this.stringValues.remove(name);
            } else {
               this.stringValues.put(name, value);
            }
         }
      });
   }

   @Override
   public OptionalBoolean getBooleanValue(String name) {
      if (this.booleanValues.containsKey(name)) {
         return this.booleanValues.get(name) ? OptionalBoolean.TRUE : OptionalBoolean.FALSE;
      } else {
         return OptionalBoolean.DEFAULT;
      }
   }

   @Override
   public Optional<String> getStringValue(String name) {
      return Optional.ofNullable(this.stringValues.get(name));
   }

   @Override
   public int getOptionsChanged() {
      return this.stringValues.size() + this.booleanValues.size();
   }

   @Override
   public MutableOptionValues mutableCopy() {
      return new MutableOptionValues(this.options, new HashMap<>(this.booleanValues), new HashMap<>(this.stringValues));
   }

   @Override
   public ImmutableOptionValues toImmutable() {
      return new ImmutableOptionValues(this.options, ImmutableMap.copyOf(this.booleanValues), ImmutableMap.copyOf(this.stringValues));
   }

   @Override
   public OptionSet getOptionSet() {
      return this.options;
   }
}
