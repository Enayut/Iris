package net.irisshaders.iris.gui.element;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.irisshaders.iris.gui.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IrisElementRow {
   private final Map<IrisElementRow.Element, Integer> elements = new HashMap<>();
   private final List<IrisElementRow.Element> orderedElements = new ArrayList<>();
   private final int spacing;
   private int x;
   private int y;
   private int width;
   private int height;

   public IrisElementRow(int spacing) {
      this.spacing = spacing;
   }

   public IrisElementRow() {
      this(1);
   }

   public int getWidth() {
      return this.width;
   }

   public IrisElementRow add(IrisElementRow.Element element, int width) {
      if (!this.orderedElements.contains(element)) {
         this.orderedElements.add(element);
      }

      this.elements.put(element, width);
      this.width = this.width + width + this.spacing;
      return this;
   }

   public void setWidth(IrisElementRow.Element element, int width) {
      if (this.elements.containsKey(element)) {
         this.width = this.width - (this.elements.get(element) + 2);
         this.add(element, width);
      }
   }

   public void render(GuiGraphicsExtractor guiGraphics, int x, int y, int height, int mouseX, int mouseY, float tickDelta, boolean rowHovered) {
      this.x = x;
      this.y = y;
      this.height = height;
      int currentX = x;

      for (IrisElementRow.Element element : this.orderedElements) {
         int currentWidth = this.elements.get(element);
         element.render(
            guiGraphics,
            currentX,
            y,
            currentWidth,
            height,
            mouseX,
            mouseY,
            tickDelta,
            rowHovered && this.sectionHovered(currentX, currentWidth, mouseX, mouseY)
         );
         currentX += currentWidth + this.spacing;
      }
   }

   public void renderRightAligned(GuiGraphicsExtractor guiGraphics, int x, int y, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
      this.render(guiGraphics, x - this.width, y, height, mouseX, mouseY, tickDelta, hovered);
   }

   private boolean sectionHovered(int sectionX, int sectionWidth, double mx, double my) {
      return mx > sectionX && mx < sectionX + sectionWidth && my > this.y && my < this.y + this.height;
   }

   private Optional<IrisElementRow.Element> getHovered(double mx, double my) {
      int currentX = this.x;

      for (IrisElementRow.Element element : this.orderedElements) {
         int currentWidth = this.elements.get(element);
         if (this.sectionHovered(currentX, currentWidth, mx, my)) {
            return Optional.of(element);
         }

         currentX += currentWidth + this.spacing;
      }

      return Optional.empty();
   }

   private Optional<IrisElementRow.Element> getFocused() {
      return this.orderedElements.stream().filter(IrisElementRow.Element::isFocused).findFirst();
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean bl2) {
      return this.getHovered(event.x(), event.y()).map(element -> element.mouseClicked(event, bl2)).orElse(false);
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      return this.getHovered(event.x(), event.y()).map(element -> element.mouseReleased(event)).orElse(false);
   }

   public boolean keyPressed(KeyEvent event) {
      return this.getFocused().map(element -> element.keyPressed(event)).orElse(false);
   }

   public List<? extends GuiEventListener> children() {
      return ImmutableList.copyOf(this.orderedElements);
   }

   public abstract static class ButtonElement<T extends IrisElementRow.ButtonElement<T>> extends IrisElementRow.Element {
      private final Function<T, Boolean> onClick;

      protected ButtonElement(Function<T, Boolean> onClick) {
         this.onClick = onClick;
      }

      public boolean mouseClicked(MouseButtonEvent event, boolean bl2) {
         if (this.disabled) {
            return false;
         } else {
            return event.button() == 1 ? this.onClick.apply((T)this) : super.mouseClicked(event, bl2);
         }
      }

      public boolean keyPressed(KeyEvent event) {
         return event.isConfirmation() ? this.onClick.apply((T)this) : false;
      }
   }

   public abstract static class Element implements GuiEventListener {
      public boolean disabled = false;
      private boolean hovered = false;
      private boolean focused;
      private ScreenRectangle bounds = ScreenRectangle.empty();

      public void render(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
         this.bounds = new ScreenRectangle(x, y, width, height);
         GuiUtil.bindIrisWidgetsTexture();
         GuiUtil.drawButton(guiGraphics, x, y, width, height, this.isHovered() || this.isFocused(), this.disabled);
         this.hovered = hovered;
         this.renderLabel(guiGraphics, x, y, width, height, mouseX, mouseY, tickDelta, hovered);
      }

      public abstract void renderLabel(GuiGraphicsExtractor var1, int var2, int var3, int var4, int var5, int var6, int var7, float var8, boolean var9);

      public boolean isHovered() {
         return this.hovered;
      }

      public boolean isFocused() {
         return this.focused;
      }

      public void setFocused(boolean focused) {
         this.focused = focused;
      }

      @Nullable
      public ComponentPath nextFocusPath(FocusNavigationEvent pGuiEventListener0) {
         return !this.isFocused() ? ComponentPath.leaf(this) : null;
      }

      @NotNull
      public ScreenRectangle getRectangle() {
         return this.bounds;
      }
   }

   public static class IconButtonElement extends IrisElementRow.ButtonElement<IrisElementRow.IconButtonElement> {
      public final GuiUtil.Icon icon;
      public final GuiUtil.Icon hoveredIcon;

      public IconButtonElement(GuiUtil.Icon icon, GuiUtil.Icon hoveredIcon, Function<IrisElementRow.IconButtonElement, Boolean> onClick) {
         super(onClick);
         this.icon = icon;
         this.hoveredIcon = hoveredIcon;
      }

      public IconButtonElement(GuiUtil.Icon icon, Function<IrisElementRow.IconButtonElement, Boolean> onClick) {
         this(icon, icon, onClick);
      }

      @Override
      public void renderLabel(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
         int iconX = x + (int)((width - this.icon.getWidth()) * 0.5);
         int iconY = y + (int)((height - this.icon.getHeight()) * 0.5);
         GuiUtil.bindIrisWidgetsTexture();
         if (this.disabled || !hovered && !this.isFocused()) {
            this.icon.draw(guiGraphics, iconX, iconY);
         } else {
            this.hoveredIcon.draw(guiGraphics, iconX, iconY);
         }
      }
   }

   public static class TextButtonElement extends IrisElementRow.ButtonElement<IrisElementRow.TextButtonElement> {
      protected final Font font;
      public Component text;

      public TextButtonElement(Component text, Function<IrisElementRow.TextButtonElement, Boolean> onClick) {
         super(onClick);
         this.font = Minecraft.getInstance().font;
         this.text = text;
      }

      @Override
      public void renderLabel(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
         int textX = x + (int)((width - this.font.width(this.text)) * 0.5);
         int textY = y + (int)((height - 8) * 0.5);
         guiGraphics.text(this.font, this.text, textX, textY, -1);
      }
   }
}
