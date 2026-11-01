package net.irisshaders.iris.helpers;

import java.util.function.BooleanSupplier;

public enum OptionalBoolean {
   DEFAULT,
   FALSE,
   TRUE;

   public boolean orElse(boolean defaultValue) {
      return this == DEFAULT ? defaultValue : this == TRUE;
   }

   public boolean orElseGet(BooleanSupplier defaultValue) {
      return this == DEFAULT ? defaultValue.getAsBoolean() : this == TRUE;
   }
}
