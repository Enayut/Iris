package net.irisshaders.iris.gui.element;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.FileDialogUtil;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.element.widget.AbstractElementWidget;
import net.irisshaders.iris.gui.element.widget.OptionMenuConstructor;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList.Entry;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShaderPackOptionList extends IrisContainerObjectSelectionList<ShaderPackOptionList.BaseEntry> {
   private static final Identifier MENU_LIST_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/menu_background.png");
   private final List<AbstractElementWidget<?>> elementWidgets = new ArrayList<>();
   private final ShaderPackScreen screen;
   private final NavigationController navigation;
   private OptionMenuContainer container;

   public ShaderPackOptionList(
      ShaderPackScreen screen,
      NavigationController navigation,
      ShaderPack pack,
      Minecraft client,
      int width,
      int height,
      int top,
      int bottom,
      int left,
      int right
   ) {
      super(client, width, bottom, top + 4, bottom, left, right, 24);
      this.navigation = navigation;
      this.screen = screen;
      this.applyShaderPack(pack);
   }

   public void applyShaderPack(ShaderPack pack) {
      this.container = pack.getMenuContainer();
   }

   public void rebuild() {
      this.clearEntries();
      this.setScrollAmount(0.0);
      OptionMenuConstructor.constructAndApplyToScreen(this.container, this.screen, this, this.navigation);
   }

   public void refresh() {
      this.elementWidgets.forEach(widget -> widget.init(this.screen, this.navigation));
   }

   public int getRowWidth() {
      return Math.min(400, this.width - 12);
   }

   protected void extractListBackground(GuiGraphicsExtractor pAbstractSelectionList0) {
      float transition = this.screen.listTransition.getAsFloat();
      pAbstractSelectionList0.blit(
         RenderPipelines.GUI_TEXTURED,
         MENU_LIST_BACKGROUND,
         this.getX(),
         this.getY(),
         this.getRight(),
         this.getBottom() + (int)this.scrollAmount(),
         this.getWidth(),
         this.getHeight(),
         32,
         32
      );
   }

   protected void extractListSeparators(GuiGraphicsExtractor pAbstractSelectionList0) {
      float transition = this.screen.listTransition.getAsFloat();
      if (!(transition < 0.02F)) {
         int col = ARGB.colorFromFloat(transition, 1.0F, 1.0F, 1.0F);
         pAbstractSelectionList0.blit(
            RenderPipelines.GUI_TEXTURED, CreateWorldScreen.HEADER_SEPARATOR, this.getX(), this.getY() - 2, 0.0F, 0.0F, this.getWidth(), 2, 32, 2, col
         );
         pAbstractSelectionList0.blit(
            RenderPipelines.GUI_TEXTURED, CreateWorldScreen.FOOTER_SEPARATOR, this.getX(), this.getBottom(), 0.0F, 0.0F, this.getWidth(), 2, 32, 2, col
         );
      }
   }

   public void addHeader(Component text, boolean backButton) {
      this.addEntry(new ShaderPackOptionList.HeaderEntry(this.screen, this.navigation, text, backButton));
   }

   public void addWidgets(int columns, List<AbstractElementWidget<?>> elements) {
      this.elementWidgets.addAll(elements);
      List<AbstractElementWidget<?>> row = new ArrayList<>();

      for (AbstractElementWidget<?> element : elements) {
         row.add(element);
         if (row.size() >= columns) {
            this.addEntry(new ShaderPackOptionList.ElementRowEntry(this.screen, this.navigation, row));
            row = new ArrayList<>();
         }
      }

      if (!row.isEmpty()) {
         while (row.size() < columns) {
            row.add(AbstractElementWidget.EMPTY);
         }

         this.addEntry(new ShaderPackOptionList.ElementRowEntry(this.screen, this.navigation, row));
      }
   }

   public NavigationController getNavigation() {
      return this.navigation;
   }

   public abstract static class BaseEntry extends Entry<ShaderPackOptionList.BaseEntry> {
      protected final NavigationController navigation;

      protected BaseEntry(NavigationController navigation) {
         this.navigation = navigation;
      }
   }

   public static class ElementRowEntry extends ShaderPackOptionList.BaseEntry {
      private final List<AbstractElementWidget<?>> widgets;
      private final ShaderPackScreen screen;
      private int cachedWidth;
      private int cachedPosX;

      public ElementRowEntry(ShaderPackScreen screen, NavigationController navigation, List<AbstractElementWidget<?>> widgets) {
         super(navigation);
         this.screen = screen;
         this.widgets = widgets;
      }

      public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
         this.cachedWidth = this.getContentWidth();
         this.cachedPosX = this.getContentX();
         int totalWidthWithoutMargins = this.getContentWidth() - 2 * (this.widgets.size() - 1);
         totalWidthWithoutMargins -= 3;
         float singleWidgetWidth = (float)totalWidthWithoutMargins / this.widgets.size();

         for (int i = 0; i < this.widgets.size(); i++) {
            AbstractElementWidget<?> widget = this.widgets.get(i);
            boolean widgetHovered = isHovered && this.getHoveredWidget(mouseX) == i || this.getFocused() == widget;
            widget.bounds = new ScreenRectangle(
               this.getContentX() + (int)((singleWidgetWidth + 2.0F) * i), this.getContentY(), (int)singleWidgetWidth, this.getContentHeight() + 2
            );
            widget.render(guiGraphics, mouseX, mouseY, tickDelta, widgetHovered);
            this.screen.setElementHoveredStatus(widget, widgetHovered);
         }
      }

      public int getHoveredWidget(int mouseX) {
         float positionAcrossWidget = (float)Mth.clamp(mouseX - this.cachedPosX, 0, this.cachedWidth) / this.cachedWidth;
         return Mth.clamp((int)Math.floor(this.widgets.size() * positionAcrossWidget), 0, this.widgets.size() - 1);
      }

      public boolean mouseClicked(MouseButtonEvent event, boolean bl2) {
         return this.widgets.get(this.getHoveredWidget((int)event.x())).mouseClicked(event, bl2);
      }

      public boolean mouseReleased(MouseButtonEvent event) {
         return this.widgets.get(this.getHoveredWidget((int)event.x())).mouseReleased(event);
      }

      @NotNull
      public List<? extends GuiEventListener> children() {
         return ImmutableList.copyOf(this.widgets);
      }

      @NotNull
      public List<? extends NarratableEntry> narratables() {
         return ImmutableList.copyOf(this.widgets);
      }
   }

   public class HeaderEntry extends ShaderPackOptionList.BaseEntry {
      public static final Component BACK_BUTTON_TEXT = Component.literal("< ")
         .append(Component.translatable("options.iris.back").withStyle(ChatFormatting.ITALIC));
      public static final MutableComponent RESET_BUTTON_TEXT_INACTIVE = Component.translatable("options.iris.reset").withStyle(ChatFormatting.GRAY);
      public static final MutableComponent RESET_BUTTON_TEXT_ACTIVE = Component.translatable("options.iris.reset").withStyle(ChatFormatting.YELLOW);
      public static final MutableComponent RESET_HOLD_SHIFT_TOOLTIP = Component.translatable("options.iris.reset.tooltip.holdShift")
         .withStyle(ChatFormatting.GOLD);
      public static final MutableComponent RESET_TOOLTIP = Component.translatable("options.iris.reset.tooltip").withStyle(ChatFormatting.RED);
      public static final MutableComponent IMPORT_TOOLTIP = Component.translatable("options.iris.importSettings.tooltip")
         .withStyle(style -> style.withColor(TextColor.fromRgb(-11688193)));
      public static final MutableComponent EXPORT_TOOLTIP = Component.translatable("options.iris.exportSettings.tooltip")
         .withStyle(style -> style.withColor(TextColor.fromRgb(-230083)));
      private static final int MIN_SIDE_BUTTON_WIDTH = 42;
      private static final int BUTTON_HEIGHT = 16;
      private final ShaderPackScreen screen;
      @Nullable
      private final IrisElementRow backButton;
      private final IrisElementRow utilityButtons = new IrisElementRow();
      private final IrisElementRow.TextButtonElement resetButton;
      private final IrisElementRow.IconButtonElement importButton;
      private final IrisElementRow.IconButtonElement exportButton;
      private final Component text;

      public HeaderEntry(ShaderPackScreen screen, NavigationController navigation, Component text, boolean hasBackButton) {
         super(navigation);
         if (hasBackButton) {
            this.backButton = new IrisElementRow()
               .add(
                  new IrisElementRow.TextButtonElement(BACK_BUTTON_TEXT, this::backButtonClicked),
                  Math.max(42, Minecraft.getInstance().font.width(BACK_BUTTON_TEXT) + 8)
               );
         } else {
            this.backButton = null;
         }

         this.resetButton = new IrisElementRow.TextButtonElement(RESET_BUTTON_TEXT_INACTIVE, this::resetButtonClicked);
         this.importButton = new IrisElementRow.IconButtonElement(GuiUtil.Icon.IMPORT, GuiUtil.Icon.IMPORT_COLORED, this::importSettingsButtonClicked);
         this.exportButton = new IrisElementRow.IconButtonElement(GuiUtil.Icon.EXPORT, GuiUtil.Icon.EXPORT_COLORED, this::exportSettingsButtonClicked);
         this.utilityButtons
            .add(this.importButton, 15)
            .add(this.exportButton, 15)
            .add(this.resetButton, Math.max(42, Minecraft.getInstance().font.width(RESET_BUTTON_TEXT_INACTIVE) + 8));
         this.screen = screen;
         this.text = text;
      }

      public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
         int x = this.getX();
         int y = this.getY();
         int entryWidth = this.getWidth();
         int entryHeight = this.getHeight();
         guiGraphics.fill(x - 3, y + entryHeight - 2, x + entryWidth, y + entryHeight - 1, 1723776702);
         Font font = Minecraft.getInstance().font;
         guiGraphics.textRenderer()
            .acceptScrolling(this.text, x + (int)(entryWidth * 0.5), x + 5, x + entryWidth - 10 - this.utilityButtons.getWidth(), y + 5, y + 15);
         GuiUtil.bindIrisWidgetsTexture();
         if (this.backButton != null) {
            this.backButton.render(guiGraphics, x, y, 16, mouseX, mouseY, tickDelta, isHovered);
         }

         boolean shiftDown = Minecraft.getInstance().hasShiftDown();
         this.resetButton.disabled = !shiftDown && !this.resetButton.isFocused();
         this.resetButton.text = !this.resetButton.disabled ? RESET_BUTTON_TEXT_ACTIVE : RESET_BUTTON_TEXT_INACTIVE;
         this.utilityButtons.renderRightAligned(guiGraphics, x + entryWidth - 3, y, 16, mouseX, mouseY, tickDelta, isHovered);
         if (this.resetButton.isHovered() || this.resetButton.isFocused()) {
            Component tooltip = !this.resetButton.disabled ? RESET_TOOLTIP : RESET_HOLD_SHIFT_TOOLTIP;
            this.queueBottomRightAnchoredTooltip(
               guiGraphics,
               this.resetButton.getRectangle().getBoundInDirection(ScreenDirection.RIGHT),
               this.resetButton.getRectangle().position().y(),
               font,
               tooltip
            );
         }

         if (this.importButton.isHovered() || this.importButton.isFocused()) {
            this.queueBottomRightAnchoredTooltip(
               guiGraphics,
               this.importButton.getRectangle().getBoundInDirection(ScreenDirection.RIGHT),
               this.importButton.getRectangle().position().y(),
               font,
               IMPORT_TOOLTIP
            );
         }

         if (this.exportButton.isHovered() || this.exportButton.isFocused()) {
            this.queueBottomRightAnchoredTooltip(
               guiGraphics,
               this.exportButton.getRectangle().getBoundInDirection(ScreenDirection.RIGHT),
               this.exportButton.getRectangle().position().y(),
               font,
               EXPORT_TOOLTIP
            );
         }
      }

      private void queueBottomRightAnchoredTooltip(GuiGraphicsExtractor guiGraphics, int x, int y, Font font, Component text) {
         ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, guiGraphics, text, x - (font.width(text) + 10), y - 16));
      }

      public List<? extends GuiEventListener> children() {
         return this.backButton != null
            ? ImmutableList.copyOf(Iterables.concat(this.utilityButtons.children(), this.backButton.children()))
            : ImmutableList.copyOf(this.utilityButtons.children());
      }

      public boolean mouseClicked(MouseButtonEvent event, boolean bl2) {
         boolean backButtonResult = this.backButton != null && this.backButton.mouseClicked(event, bl2);
         boolean utilButtonResult = this.utilityButtons.mouseClicked(event, bl2);
         return backButtonResult || utilButtonResult;
      }

      public boolean keyPressed(KeyEvent event) {
         return this.backButton != null && this.backButton.keyPressed(event) ? true : this.utilityButtons.keyPressed(event);
      }

      public List<? extends NarratableEntry> narratables() {
         return ImmutableList.of();
      }

      private boolean backButtonClicked(IrisElementRow.TextButtonElement button) {
         this.navigation.back();
         GuiUtil.playButtonClickSound();
         return true;
      }

      private boolean resetButtonClicked(IrisElementRow.TextButtonElement button) {
         if (Minecraft.getInstance().hasShiftDown()) {
            Iris.resetShaderPackOptionsOnNextReload();
            this.screen.applyChanges();
            GuiUtil.playButtonClickSound();
            return true;
         } else {
            return false;
         }
      }

      private boolean importSettingsButtonClicked(IrisElementRow.IconButtonElement button) {
         GuiUtil.playButtonClickSound();
         if (Iris.getCurrentPack().isEmpty()) {
            return false;
         } else if ((Boolean)Minecraft.getInstance().options.fullscreen().get()) {
            this.screen
               .displayNotification(Component.translatable("options.iris.mustDisableFullscreen").withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD));
            return false;
         } else {
            ShaderPackScreen originalScreen = this.screen;
            FileDialogUtil.fileSelectDialog(
                  FileDialogUtil.DialogType.OPEN,
                  "Import Shader Settings from File",
                  Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt"),
                  "Shader Pack Settings (.txt)",
                  "*.txt"
               )
               .whenComplete((path, err) -> {
                  if (err != null) {
                     Iris.logger.error("Error selecting shader settings from file", err);
                  } else {
                     if (Minecraft.getInstance().gui.screen() == originalScreen) {
                        path.ifPresent(originalScreen::importPackOptions);
                     }
                  }
               });
            return true;
         }
      }

      private boolean exportSettingsButtonClicked(IrisElementRow.IconButtonElement button) {
         GuiUtil.playButtonClickSound();
         if (Iris.getCurrentPack().isEmpty()) {
            return false;
         } else if ((Boolean)Minecraft.getInstance().options.fullscreen().get()) {
            this.screen
               .displayNotification(Component.translatable("options.iris.mustDisableFullscreen").withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD));
            return false;
         } else {
            FileDialogUtil.fileSelectDialog(
                  FileDialogUtil.DialogType.SAVE,
                  "Export Shader Settings to File",
                  Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt"),
                  "Shader Pack Settings (.txt)",
                  "*.txt"
               )
               .whenComplete((path, err) -> {
                  if (err != null) {
                     Iris.logger.error("Error selecting file to export shader settings", err);
                  } else {
                     path.ifPresent(p -> {
                        Properties toSave = new Properties();
                        Path sourceTxtPath = Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt");
                        if (Files.exists(sourceTxtPath)) {
                           try (InputStream in = Files.newInputStream(sourceTxtPath)) {
                              toSave.load(in);
                           } catch (IOException var11) {
                           }
                        }

                        try (OutputStream out = Files.newOutputStream(p)) {
                           toSave.store(out, null);
                        } catch (IOException e) {
                           Iris.logger.error("Error saving properties to \"" + p + "\"", e);
                        }
                     });
                  }
               });
            return true;
         }
      }
   }
}
