package net.irisshaders.iris.gui.element.widget;

import java.util.Optional;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuLinkElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class LinkElementWidget extends CommentedElementWidget<OptionMenuLinkElement> {
   private static final Component ARROW = Component.literal(">");
   private final String targetScreenId;
   private final MutableComponent label;
   private NavigationController navigation;
   private MutableComponent trimmedLabel = null;
   private boolean isLabelTrimmed = false;

   public LinkElementWidget(OptionMenuLinkElement element) {
      super(element);
      this.targetScreenId = element.targetScreenId;
      this.label = GuiUtil.translateOrDefault(Component.literal(element.targetScreenId), "screen." + element.targetScreenId);
   }

   @Override
   public void init(ShaderPackScreen screen, NavigationController navigation) {
      this.navigation = navigation;
   }

   @Override
   public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float tickDelta, boolean hovered) {
      GuiUtil.bindIrisWidgetsTexture();
      GuiUtil.drawButton(
         guiGraphics, this.bounds.position().x(), this.bounds.position().y(), this.bounds.width(), this.bounds.height(), hovered || this.isFocused(), false
      );
      Font font = Minecraft.getInstance().font;
      int maxLabelWidth = this.bounds.width() - 9;
      if (font.width(this.label) > maxLabelWidth) {
         this.isLabelTrimmed = true;
      }

      if (this.trimmedLabel == null) {
         this.trimmedLabel = GuiUtil.shortenText(font, this.label, maxLabelWidth);
      }

      int labelWidth = font.width(this.trimmedLabel);
      guiGraphics.text(
         font,
         this.trimmedLabel,
         this.bounds.getCenterInAxis(ScreenAxis.HORIZONTAL) - (int)(labelWidth * 0.5) - (int)(0.5 * Math.max(labelWidth - (this.bounds.width() - 18), 0)),
         this.bounds.position().y() + 7,
         -1
      );
      guiGraphics.text(font, ARROW, this.bounds.getBoundInDirection(ScreenDirection.RIGHT) - 9, this.bounds.position().y() + 7, 268435455);
      if (hovered && this.isLabelTrimmed) {
         ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, guiGraphics, this.label, mouseX + 2, mouseY - 16));
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean bl2) {
      if (event.button() == 1) {
         this.navigation.open(this.targetScreenId);
         GuiUtil.playButtonClickSound();
         return true;
      } else {
         return super.mouseClicked(event, bl2);
      }
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (event.isConfirmation()) {
         this.navigation.open(this.targetScreenId);
         GuiUtil.playButtonClickSound();
         return true;
      } else {
         return super.keyPressed(event);
      }
   }

   @Override
   public Optional<Component> getCommentTitle() {
      return Optional.of(this.label);
   }

   @Override
   public Optional<Component> getCommentBody() {
      String translation = "screen." + this.targetScreenId + ".comment";
      return Optional.ofNullable(Language.getInstance().has(translation) ? Component.translatable(translation) : null);
   }
}
