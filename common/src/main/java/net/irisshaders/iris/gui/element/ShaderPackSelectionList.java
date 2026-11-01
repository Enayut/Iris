package net.irisshaders.iris.gui.element;

import com.mojang.blaze3d.Blaze3D;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList.Entry;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

public class ShaderPackSelectionList extends IrisObjectSelectionList<ShaderPackSelectionList.BaseEntry> {
   private static final Component PACK_LIST_LABEL = Component.translatable("pack.iris.list.label")
      .withStyle(new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.GRAY});
   private static final Identifier MENU_LIST_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/menu_background.png");
   private final ShaderPackScreen screen;
   private final ShaderPackSelectionList.TopButtonRowEntry topButtonRow;
   private final WatchService watcher;
   private final WatchKey key;
   private final ShaderPackSelectionList.PinnedEntry downloadButton;
   private boolean keyValid;
   private ShaderPackSelectionList.ShaderPackEntry applied = null;

   public ShaderPackSelectionList(ShaderPackScreen screen, Minecraft client, int width, int height, int top, int bottom, int left, int right) {
      super(client, width, bottom, top + 4, bottom, left, right, 20);
      this.screen = screen;
      this.topButtonRow = new ShaderPackSelectionList.TopButtonRowEntry(this, Iris.getIrisConfig().areShadersEnabled());
      this.downloadButton = new ShaderPackSelectionList.PinnedEntry(
         Component.literal("Download Shaders"), () -> this.minecraft.gui.setScreen(new ConfirmLinkScreen(bl -> {
            if (bl) {
               Blaze3D.openUri(URI.create("https://modrinth.com/shaders"));
            }

            this.minecraft.gui.setScreen(this.screen);
         }, URI.create("https://modrinth.com/shaders"), true)), this
      );

      WatchKey key1;
      WatchService watcher1;
      try {
         watcher1 = FileSystems.getDefault().newWatchService();
         key1 = Iris.getShaderpacksDirectory()
            .register(watcher1, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
         this.keyValid = true;
      } catch (IOException e) {
         Iris.logger.error("Couldn't register file watcher!", e);
         watcher1 = null;
         key1 = null;
         this.keyValid = false;
      }

      this.key = key1;
      this.watcher = watcher1;
      this.refresh();
   }

   public boolean keyPressed(KeyEvent event) {
      return event.isUp() && this.getFocused() == this.children().getFirst() ? true : super.keyPressed(event);
   }

   public void extractWidgetRenderState(GuiGraphicsExtractor pAbstractSelectionList0, int pInt1, int pInt2, float pFloat3) {
      if (this.keyValid) {
         for (WatchEvent<?> event : this.key.pollEvents()) {
            if (event.kind() != StandardWatchEventKinds.OVERFLOW) {
               this.refresh();
               break;
            }
         }

         this.keyValid = this.key.reset();
      }

      super.extractWidgetRenderState(pAbstractSelectionList0, pInt1, pInt2, pFloat3);
   }

   public void close() throws IOException {
      if (this.key != null) {
         this.key.cancel();
      }

      if (this.watcher != null) {
         this.watcher.close();
      }
   }

   protected void extractListBackground(GuiGraphicsExtractor pAbstractSelectionList0) {
      float transition = this.screen.listTransition.getAsFloat();
      if (!(transition < 0.02F)) {
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

   public int getRowWidth() {
      return Math.min(308, this.width - 50);
   }

   public int getRowTop(int index) {
      return super.getRowTop(index) + 2;
   }

   public void refresh() {
      this.clearEntries();

      List<String> names;
      try {
         names = Iris.getShaderpacksDirectoryManager().enumerate();
      } catch (Throwable e) {
         Iris.logger.error("Error reading files while constructing selection UI", e);
         this.addLabelEntries(
            Component.empty(),
            Component.literal("There was an error reading your shaderpacks directory").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}),
            Component.empty(),
            Component.literal("Check your logs for more information."),
            Component.literal("Please file an issue report including a log file."),
            Component.literal("If you are able to identify the file causing this, please include it in your report as well."),
            Component.literal("Note that this might be an issue with folder permissions; ensure those are correct first.")
         );
         return;
      }

      this.addEntry(this.topButtonRow);
      if (names.isEmpty()) {
         this.addEntry(this.downloadButton);
      }

      this.topButtonRow.allowEnableShadersButton = !names.isEmpty();
      int index = 0;
      String selectedName = Iris.getIrisConfig().getShaderPackName().orElse(null);
      ShaderPackSelectionList.ShaderPackEntry selectedPack = null;

      for (String name : names) {
         ShaderPackSelectionList.ShaderPackEntry entry = this.addPackEntry(++index, name);
         if (name.equals(selectedName)) {
            selectedPack = entry;
         }
      }

      if (selectedPack != null) {
         this.setSelected(selectedPack);
         this.setFocused(selectedPack);
         this.centerScrollOn(selectedPack);
         this.setApplied(selectedPack);
      }

      this.addLabelEntries(PACK_LIST_LABEL);
   }

   public ShaderPackSelectionList.ShaderPackEntry addPackEntry(int index, String name) {
      ShaderPackSelectionList.ShaderPackEntry entry = new ShaderPackSelectionList.ShaderPackEntry(index, this, name);
      this.addEntry(entry);
      return entry;
   }

   public void addLabelEntries(Component... lines) {
      for (Component text : lines) {
         this.addEntry(new ShaderPackSelectionList.LabelEntry(text));
      }
   }

   public void select(String name) {
      for (int i = 0; i < this.getItemCount(); i++) {
         ShaderPackSelectionList.BaseEntry entry = (ShaderPackSelectionList.BaseEntry)this.children().get(i);
         if (entry instanceof ShaderPackSelectionList.ShaderPackEntry && ((ShaderPackSelectionList.ShaderPackEntry)entry).packName.equals(name)) {
            this.setSelected(entry);
            return;
         }
      }
   }

   public ShaderPackSelectionList.ShaderPackEntry getApplied() {
      return this.applied;
   }

   public void setApplied(ShaderPackSelectionList.ShaderPackEntry entry) {
      this.applied = entry;
   }

   public ShaderPackSelectionList.TopButtonRowEntry getTopButtonRow() {
      return this.topButtonRow;
   }

   public abstract static class BaseEntry extends Entry<ShaderPackSelectionList.BaseEntry> {
      protected BaseEntry() {
      }
   }

   public static class LabelEntry extends ShaderPackSelectionList.BaseEntry {
      private final Component label;

      public LabelEntry(Component label) {
         this.label = label;
      }

      public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
         int x = this.getContentX();
         int y = this.getContentY();
         int entryWidth = this.getContentWidth();
         int entryHeight = this.getContentHeight();
         guiGraphics.centeredText(Minecraft.getInstance().font, this.label, x + entryWidth / 2 - 2, y + (entryHeight - 11) / 2, -4013374);
      }
   }

   private static class PinnedEntry extends ShaderPackSelectionList.BaseEntry {
      public final boolean allowPressButton = true;
      private final Component label;
      private final Runnable onClick;

      public PinnedEntry(Component label, Runnable onClick, ShaderPackSelectionList list) {
         this.label = label;
         this.onClick = onClick;
      }

      public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
         int x = this.getContentX();
         int y = this.getContentY();
         int entryWidth = this.getContentWidth();
         int entryHeight = this.getContentHeight();
         GuiUtil.bindIrisWidgetsTexture();
         GuiUtil.drawButton(guiGraphics, x - 2, y - 2, entryWidth, entryHeight + 2, isHovered, false);
         guiGraphics.centeredText(Minecraft.getInstance().font, this.label, x + entryWidth / 2 - 2, y + (entryHeight - 11) / 2, -1);
      }

      public boolean mouseClicked(MouseButtonEvent event, boolean repeat) {
         Objects.requireNonNull(this);
         GuiUtil.playButtonClickSound();
         this.onClick.run();
         return false;
      }

      public boolean keyPressed(KeyEvent event) {
         if (event.isConfirmation()) {
            Objects.requireNonNull(this);
            GuiUtil.playButtonClickSound();
            this.onClick.run();
            return false;
         } else {
            return false;
         }
      }
   }

   public class ShaderPackEntry extends ShaderPackSelectionList.BaseEntry {
      private final String packName;
      private final ShaderPackSelectionList list;
      private final int index;
      private ScreenRectangle bounds = ScreenRectangle.empty();
      private boolean focused;

      public ShaderPackEntry(int index, ShaderPackSelectionList list, String packName) {
         this.packName = packName;
         this.list = list;
         this.index = index;
      }

      public ScreenRectangle getRectangle() {
         return this.bounds;
      }

      public boolean isApplied() {
         return this.list.getApplied() == this;
      }

      public boolean isSelected() {
         return this.list.getSelected() == this;
      }

      public String getPackName() {
         return this.packName;
      }

      public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
         int x = this.getContentX();
         int y = this.getContentY();
         int entryWidth = this.getContentWidth();
         int entryHeight = this.getContentHeight();
         this.bounds = new ScreenRectangle(x, y, entryWidth, entryHeight);
         Font font = Minecraft.getInstance().font;
         int color = -1;
         String name = this.packName;
         if (isHovered) {
            GuiUtil.bindIrisWidgetsTexture();
            GuiUtil.drawButton(guiGraphics, x - 2, y - 2, entryWidth + 4, entryHeight + 4, isHovered, false);
         }

         boolean shadersEnabled = this.list.getTopButtonRow().shadersEnabled;
         if (font.width(Component.literal(name).withStyle(ChatFormatting.BOLD)) > this.list.getRowWidth() - 3) {
            name = font.plainSubstrByWidth(name, this.list.getRowWidth() - 8) + "...";
         }

         MutableComponent text = Component.literal(name);
         if (this.isMouseOver(mouseX, mouseY)) {
            text = text.withStyle(ChatFormatting.BOLD);
         }

         if (shadersEnabled && this.isApplied()) {
            color = -3485;
         }

         if (!shadersEnabled && !this.isMouseOver(mouseX, mouseY)) {
            color = -6118750;
         }

         guiGraphics.centeredText(font, text, x + entryWidth / 2 - 2, y + (entryHeight - 11) / 2, color);
      }

      public boolean mouseClicked(MouseButtonEvent event, boolean repeat) {
         return event.button() != 1 ? false : this.doThing();
      }

      public boolean keyPressed(KeyEvent event) {
         return !event.isConfirmation() ? false : this.doThing();
      }

      private boolean doThing() {
         boolean didAnything = false;
         if (!this.list.getTopButtonRow().shadersEnabled) {
            this.list.getTopButtonRow().setShadersEnabled(true);
            didAnything = true;
         }

         if (!this.isSelected()) {
            this.list.select(this.index);
            didAnything = true;
         }

         ShaderPackSelectionList.this.screen.setFocused(ShaderPackSelectionList.this.screen.getBottomRowOption());
         return didAnything;
      }

      @Nullable
      public ComponentPath nextFocusPath(FocusNavigationEvent pGuiEventListener0) {
         return !this.isFocused() ? ComponentPath.leaf(this) : null;
      }

      public boolean isFocused() {
         return this.list.getFocused() == this;
      }
   }

   public static class TopButtonRowEntry extends ShaderPackSelectionList.BaseEntry {
      private static final Component NONE_PRESENT_LABEL = Component.translatable("options.iris.shaders.nonePresent").withStyle(ChatFormatting.GRAY);
      private static final Component SHADERS_DISABLED_LABEL = Component.translatable("options.iris.shaders.disabled");
      private static final Component SHADERS_ENABLED_LABEL = Component.translatable("options.iris.shaders.enabled");
      private final ShaderPackSelectionList list;
      public boolean allowEnableShadersButton = true;
      public boolean shadersEnabled;

      public TopButtonRowEntry(ShaderPackSelectionList list, boolean shadersEnabled) {
         this.list = list;
         this.shadersEnabled = shadersEnabled;
      }

      public void setShadersEnabled(boolean shadersEnabled) {
         this.shadersEnabled = shadersEnabled;
         this.list.screen.refreshScreenSwitchButton();
      }

      public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
         int x = this.getContentX();
         int y = this.getContentY();
         int entryWidth = this.getContentWidth();
         int entryHeight = this.getContentHeight();
         GuiUtil.bindIrisWidgetsTexture();
         GuiUtil.drawButton(guiGraphics, x - 2, y - 2, entryWidth, entryHeight + 2, isHovered, !this.allowEnableShadersButton);
         guiGraphics.centeredText(Minecraft.getInstance().font, this.getEnableDisableLabel(), x + entryWidth / 2 - 2, y + (entryHeight - 11) / 2, -1);
      }

      private Component getEnableDisableLabel() {
         return this.allowEnableShadersButton ? (this.shadersEnabled ? SHADERS_ENABLED_LABEL : SHADERS_DISABLED_LABEL) : NONE_PRESENT_LABEL;
      }

      public boolean mouseClicked(MouseButtonEvent event, boolean bl2) {
         if (this.allowEnableShadersButton) {
            this.setShadersEnabled(!this.shadersEnabled);
            GuiUtil.playButtonClickSound();
            return true;
         } else {
            return false;
         }
      }

      public boolean keyPressed(KeyEvent event) {
         if (event.isConfirmation() && this.allowEnableShadersButton) {
            this.setShadersEnabled(!this.shadersEnabled);
            GuiUtil.playButtonClickSound();
            return true;
         } else {
            return false;
         }
      }

      @Nullable
      public ComponentPath nextFocusPath(FocusNavigationEvent pGuiEventListener0) {
         return !this.isFocused() ? ComponentPath.leaf(this) : null;
      }

      public boolean isFocused() {
         return this.list.getFocused() == this;
      }

      public static class EnableShadersButtonElement extends IrisElementRow.TextButtonElement {
         private int centerX;

         public EnableShadersButtonElement(Component text, Function<IrisElementRow.TextButtonElement, Boolean> onClick) {
            super(text, onClick);
         }

         @Override
         public void renderLabel(
            GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered
         ) {
            int textX = this.centerX - (int)(this.font.width(this.text) * 0.5);
            int textY = y + (int)((height - 8) * 0.5);
            guiGraphics.text(this.font, this.text, textX, textY, -1);
         }
      }
   }
}
