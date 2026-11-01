package net.irisshaders.iris.shaderpack.option;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import net.irisshaders.iris.Iris;

public class OptionSet {
   private final ImmutableMap<String, MergedBooleanOption> booleanOptions;
   private final ImmutableMap<String, MergedStringOption> stringOptions;

   private OptionSet(OptionSet.Builder builder) {
      this.booleanOptions = ImmutableMap.copyOf(builder.booleanOptions);
      this.stringOptions = ImmutableMap.copyOf(builder.stringOptions);
   }

   public static OptionSet.Builder builder() {
      return new OptionSet.Builder();
   }

   public ImmutableMap<String, MergedBooleanOption> getBooleanOptions() {
      return this.booleanOptions;
   }

   public ImmutableMap<String, MergedStringOption> getStringOptions() {
      return this.stringOptions;
   }

   public boolean isBooleanOption(String name) {
      return this.booleanOptions.containsKey(name);
   }

   public static class Builder {
      private final Map<String, MergedBooleanOption> booleanOptions = new HashMap<>();
      private final Map<String, MergedStringOption> stringOptions = new HashMap<>();

      public void addAll(OptionSet other) {
         if (this.booleanOptions.isEmpty()) {
            this.booleanOptions.putAll(other.booleanOptions);
         } else {
            other.booleanOptions.values().forEach(this::addBooleanOption);
         }

         if (this.stringOptions.isEmpty()) {
            this.stringOptions.putAll(other.stringOptions);
         } else {
            other.stringOptions.values().forEach(this::addStringOption);
         }
      }

      public void addBooleanOption(OptionLocation location, BooleanOption option) {
         this.addBooleanOption(new MergedBooleanOption(location, option));
      }

      public void addBooleanOption(MergedBooleanOption proposed) {
         BooleanOption option = proposed.getOption();
         MergedBooleanOption existing = this.booleanOptions.get(option.getName());
         MergedBooleanOption merged;
         if (existing != null) {
            merged = existing.merge(proposed);
            if (merged == null) {
               Iris.logger.warn("Ignoring ambiguous boolean option " + option.getName());
               this.booleanOptions.remove(option.getName());
               return;
            }
         } else {
            merged = proposed;
         }

         this.booleanOptions.put(option.getName(), merged);
      }

      public void addStringOption(OptionLocation location, StringOption option) {
         this.addStringOption(new MergedStringOption(location, option));
      }

      public void addStringOption(MergedStringOption proposed) {
         StringOption option = proposed.getOption();
         MergedStringOption existing = this.stringOptions.get(option.getName());
         MergedStringOption merged;
         if (existing != null) {
            merged = existing.merge(proposed);
            if (merged == null) {
               Iris.logger.warn("Ignoring ambiguous string option " + option.getName());
               this.stringOptions.remove(option.getName());
               return;
            }
         } else {
            merged = proposed;
         }

         this.stringOptions.put(option.getName(), merged);
      }

      public OptionSet build() {
         return new OptionSet(this);
      }
   }
}
