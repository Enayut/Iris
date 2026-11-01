package net.irisshaders.iris.gui.element.widget;

import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuElement;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractElementWidget<T extends OptionMenuElement> implements GuiEventListener, NarratableEntry {
   public static final AbstractElementWidget<OptionMenuElement> EMPTY = new AbstractElementWidget<OptionMenuElement>(null) {
      @Override
      public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float tickDelta, boolean hovered) {
      }

      @Nullable
      @Override
      public ComponentPath nextFocusPath(FocusNavigationEvent pGuiEventListener0) {
         return null;
      }

      @NotNull
      @Override
      public ScreenRectangle getRectangle() {
         return ScreenRectangle.empty();
      }
   };
   protected final T element;
   public ScreenRectangle bounds = ScreenRectangle.empty();
   private boolean focused;

   public AbstractElementWidget(T element) {
      this.element = element;
   }

   public void init(ShaderPackScreen screen, NavigationController navigation) {
   }

   public abstract void render(GuiGraphicsExtractor var1, int var2, int var3, float var4, boolean var5);

   public boolean mouseClicked(MouseButtonEvent event, boolean bl2) {
      return false;
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      return false;
   }

   public boolean keyPressed(KeyEvent event) {
      return false;
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

   public ScreenRectangle getRectangle() {
      return this.bounds;
   }

   public NarrationPriority narrationPriority() {
      return NarrationPriority.NONE;
   }

   public void updateNarration(NarrationElementOutput p0) {
   }
}
