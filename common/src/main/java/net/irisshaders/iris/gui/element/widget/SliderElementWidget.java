package net.irisshaders.iris.gui.element.widget;

import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuStringOptionElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

public class SliderElementWidget extends StringElementWidget {
   private static final int PREVIEW_SLIDER_WIDTH = 4;
   private static final int ACTIVE_SLIDER_WIDTH = 6;
   private boolean mouseDown = false;

   public SliderElementWidget(OptionMenuStringOptionElement element) {
      super(element);
   }

   @Override
   public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float tickDelta, boolean hovered) {
      this.updateRenderParams(35);
      if (!hovered && !this.isFocused()) {
         if (this.usedKeyboard) {
            this.usedKeyboard = false;
            this.mouseDown = false;
         }

         this.renderOptionWithValue(guiGraphics, false, (float)this.valueIndex / (this.valueCount - 1), 4);
      } else {
         this.renderSlider(guiGraphics);
      }

      if (this.usedKeyboard) {
         if (Minecraft.getInstance().hasShiftDown()) {
            this.renderTooltip(guiGraphics, SET_TO_DEFAULT, this.bounds.getBoundInDirection(ScreenDirection.RIGHT), this.bounds.position().y(), hovered);
         } else if (!this.screen.isDisplayingComment()) {
            this.renderTooltip(guiGraphics, this.unmodifiedLabel, this.bounds.getBoundInDirection(ScreenDirection.RIGHT), this.bounds.position().y(), hovered);
         }
      } else if (Minecraft.getInstance().hasShiftDown()) {
         this.renderTooltip(guiGraphics, SET_TO_DEFAULT, mouseX, mouseY, hovered);
      } else if (!this.screen.isDisplayingComment()) {
         this.renderTooltip(guiGraphics, this.unmodifiedLabel, mouseX, mouseY, hovered);
      }

      if (this.usedKeyboard && !this.isFocused()) {
         this.usedKeyboard = false;
         this.onReleased();
      }

      if (this.mouseDown && !this.usedKeyboard) {
         if (!hovered) {
            this.onReleased();
         }

         this.whileDragging(mouseX);
      }
   }

   private void renderSlider(GuiGraphicsExtractor guiGraphics) {
      GuiUtil.bindIrisWidgetsTexture();
      GuiUtil.drawButton(
         guiGraphics, this.bounds.position().x(), this.bounds.position().y(), this.bounds.width(), this.bounds.height(), this.isFocused(), false
      );
      GuiUtil.drawButton(
         guiGraphics, this.bounds.position().x() + 2, this.bounds.position().y() + 2, this.bounds.width() - 4, this.bounds.height() - 4, false, true
      );
      int sliderSpace = this.bounds.width() - 8 - 6;
      int sliderPos = this.bounds.position().x() + 4 + (int)((float)this.valueIndex / (this.valueCount - 1) * sliderSpace);
      GuiUtil.drawButton(guiGraphics, sliderPos, this.bounds.position().y() + 4, 6, this.bounds.height() - 8, this.mouseDown, false);
      Font font = Minecraft.getInstance().font;
      guiGraphics.text(
         font,
         this.valueLabel,
         this.bounds.getCenterInAxis(ScreenAxis.HORIZONTAL) - (int)(font.width(this.valueLabel) * 0.5),
         this.bounds.position().y() + 7,
         -1
      );
   }

   private void whileDragging(int mouseX) {
      float mousePositionAcrossWidget = Mth.clamp((float)(mouseX - (this.bounds.position().x() + 4)) / (this.bounds.width() - 8), 0.0F, 1.0F);
      int newValueIndex = Math.min(this.valueCount - 1, (int)(mousePositionAcrossWidget * this.valueCount));
      if (this.valueIndex != newValueIndex) {
         this.valueIndex = newValueIndex;
         this.updateLabels();
      }
   }

   private void onReleased() {
      this.mouseDown = false;
      this.queue();
      this.navigation.refresh();
      GuiUtil.playButtonClickSound();
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean bl2) {
      if (event.button() == 1) {
         if (Minecraft.getInstance().hasShiftDown()) {
            if (this.applyOriginalValue()) {
               this.navigation.refresh();
            }

            GuiUtil.playButtonClickSound();
            return true;
         } else {
            this.mouseDown = true;
            GuiUtil.playButtonClickSound();
            return true;
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (event.isConfirmation()) {
         if (Minecraft.getInstance().hasShiftDown()) {
            if (this.applyOriginalValue()) {
               this.navigation.refresh();
            }

            GuiUtil.playButtonClickSound();
            return true;
         } else {
            this.mouseDown = !this.mouseDown;
            this.usedKeyboard = true;
            GuiUtil.playButtonClickSound();
            return true;
         }
      } else {
         if (this.mouseDown && this.usedKeyboard) {
            if (event.isLeft()) {
               this.valueIndex = Math.max(0, this.valueIndex - 1);
               this.updateLabels();
               return true;
            }

            if (event.isRight()) {
               this.valueIndex = Math.min(this.valueCount - 1, this.valueIndex + 1);
               this.updateLabels();
               return true;
            }
         }

         return false;
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent event) {
      if (event.button() == 1) {
         this.onReleased();
         return true;
      } else {
         return super.mouseReleased(event);
      }
   }
}
