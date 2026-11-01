package net.irisshaders.iris.gui.element.widget;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.option.BooleanOption;
import net.irisshaders.iris.shaderpack.option.MergedBooleanOption;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuBooleanOptionElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class BooleanElementWidget extends BaseOptionElementWidget<OptionMenuBooleanOptionElement> {
   private static final Component TEXT_TRUE = Component.translatable("label.iris.true").withStyle(ChatFormatting.GREEN);
   private static final Component TEXT_FALSE = Component.translatable("label.iris.false").withStyle(ChatFormatting.RED);
   private static final Component TEXT_TRUE_DEFAULT = Component.translatable("label.iris.true");
   private static final Component TEXT_FALSE_DEFAULT = Component.translatable("label.iris.false");
   private final BooleanOption option;
   private boolean appliedValue;
   private boolean value;
   private boolean defaultValue;

   public BooleanElementWidget(OptionMenuBooleanOptionElement element) {
      super(element);
      this.option = element.option;
   }

   @Override
   public void init(ShaderPackScreen screen, NavigationController navigation) {
      super.init(screen, navigation);
      this.appliedValue = this.element.getAppliedOptionValues().getBooleanValueOrDefault(this.option.getName());
      this.value = this.element.getPendingOptionValues().getBooleanValueOrDefault(this.option.getName());
      this.defaultValue = ((MergedBooleanOption)this.element.getAppliedOptionValues().getOptionSet().getBooleanOptions().get(this.option.getName()))
         .getOption()
         .getDefaultValue();
      this.setLabel(GuiUtil.translateOrDefault(Component.literal(this.option.getName()), "option." + this.option.getName()));
   }

   @Override
   public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float tickDelta, boolean hovered) {
      this.updateRenderParams(28);
      this.renderOptionWithValue(guiGraphics, hovered || this.isFocused());
      this.tryRenderTooltip(guiGraphics, mouseX, mouseY, hovered);
   }

   @Override
   protected Component createValueLabel() {
      if (this.value == this.defaultValue) {
         return this.value ? TEXT_TRUE_DEFAULT : TEXT_FALSE_DEFAULT;
      } else {
         return this.value ? TEXT_TRUE : TEXT_FALSE;
      }
   }

   @Override
   public String getCommentKey() {
      return "option." + this.option.getName() + ".comment";
   }

   public String getValue() {
      return Boolean.toString(this.value);
   }

   private void queue() {
      Iris.getShaderPackOptionQueue().put(this.option.getName(), this.getValue());
   }

   @Override
   public boolean applyNextValue() {
      this.value = !this.value;
      this.queue();
      return true;
   }

   @Override
   public boolean applyPreviousValue() {
      return this.applyNextValue();
   }

   @Override
   public boolean applyOriginalValue() {
      this.value = this.option.getDefaultValue();
      this.queue();
      return true;
   }

   @Override
   public boolean isValueModified() {
      return this.value != this.appliedValue;
   }
}
