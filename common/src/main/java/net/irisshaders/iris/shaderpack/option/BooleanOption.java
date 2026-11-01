package net.irisshaders.iris.shaderpack.option;

public final class BooleanOption extends BaseOption {
   private final boolean defaultValue;

   public BooleanOption(OptionType type, String name, String comment, boolean defaultValue) {
      super(type, name, comment);
      this.defaultValue = defaultValue;
   }

   public boolean getDefaultValue() {
      return this.defaultValue;
   }

   @Override
   public String toString() {
      return "BooleanDefineOption{name=" + this.getName() + ", comment=" + this.getComment() + ", defaultValue=" + this.defaultValue + "}";
   }
}
