package net.irisshaders.iris.gui.screen;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.OldImageButton;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import net.irisshaders.iris.gui.element.screen.IrisButton;
import net.irisshaders.iris.gui.element.widget.AbstractElementWidget;
import net.irisshaders.iris.gui.element.widget.CommentedElementWidget;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.transforms.SmoothedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

public class ShaderPackScreen extends Screen implements HudHideable {
   public static final Set<Runnable> TOP_LAYER_RENDER_QUEUE = new HashSet<>();
   private static final Component SELECT_TITLE = Component.translatable("pack.iris.select.title")
      .withStyle(new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC});
   private static final Component CONFIGURE_TITLE = Component.translatable("pack.iris.configure.title")
      .withStyle(new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC});
   private static final int COMMENT_PANEL_WIDTH = 314;
   private static final String development = "Development Environment";
   private final Screen parent;
   private final MutableComponent irisTextComponent;
   private final FrameUpdateNotifier notifier = new FrameUpdateNotifier();
   private ShaderPackSelectionList shaderPackList;
   @Nullable
   private ShaderPackOptionList shaderOptionList = null;
   @Nullable
   private NavigationController navigation = null;
   private Button screenSwitchButton;
   private Component notificationDialog = null;
   private int notificationDialogTimer = 0;
   @Nullable
   private AbstractElementWidget<?> hoveredElement = null;
   private Optional<Component> hoveredElementCommentTitle = Optional.empty();
   private List<FormattedCharSequence> hoveredElementCommentBody = new ArrayList<>();
   private int hoveredElementCommentTimer = 0;
   private boolean optionMenuOpen = false;
   private boolean dropChanges = false;
   private MutableComponent developmentComponent;
   private MutableComponent updateComponent;
   private boolean guiHidden = false;
   public final SmoothedFloat blurTransition = new SmoothedFloat(2.0F, 2.0F, () -> {
      if (this.guiHidden) {
         return 0.0F;
      } else {
         return this.optionMenuOpen ? 0.1F : this.minecraft.options.getMenuBackgroundBlurriness();
      }
   }, this.notifier);
   private float guiButtonHoverTimer = 0.0F;
   private Button openFolderButton;
   private float backgroundInit = 0.0F;
   public final SmoothedFloat listTransition = new SmoothedFloat(
      1.0F, 1.0F, () -> !this.guiHidden && !this.optionMenuOpen ? this.backgroundInit : 0.0F, this.notifier
   );
   public final SmoothedFloat buttonTransition = new SmoothedFloat(1.0F, 1.0F, () -> this.guiHidden ? 0.0F : this.backgroundInit, this.notifier);
   private OldImageButton showHideButton;
   private static final Identifier BLUR_POST_CHAIN_ID = Identifier.withDefaultNamespace("blur");

   public ShaderPackScreen(Screen parent) {
      super(Component.translatable("options.iris.shaderPackSelection.title"));
      this.parent = parent;
      String irisName = "Iris " + Iris.getVersion();
      if (IrisPlatformHelpers.getInstance().isDevelopmentEnvironment()) {
         this.developmentComponent = Component.literal("Development Environment").withStyle(ChatFormatting.GOLD);
      }

      this.irisTextComponent = Component.literal(irisName).withStyle(ChatFormatting.GRAY);
      if (Iris.getUpdateChecker().getUpdateMessage().isPresent()) {
         this.updateComponent = Component.literal("New update available!").withStyle(ChatFormatting.GREEN).withStyle(ChatFormatting.UNDERLINE);
         this.irisTextComponent.append(Component.literal(" (outdated)").withStyle(ChatFormatting.RED));
      }

      this.refreshForChangedPack();
   }

   protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
      float blurRadius = Math.min(this.minecraft.options.getMenuBackgroundBlurriness(), this.blurTransition.getAsFloat());
      if (blurRadius >= 1.0F) {
         graphics.blurBeforeThisStratum();
      }
   }

   public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
      this.notifier.onNewFrame();
      this.backgroundInit = 1.0F;
      if (Minecraft.getInstance().hasControlDown() && InputConstants.isKeyDown(7)) {
         Minecraft.getInstance()
            .gui
            .setScreen(
               new ConfirmScreen(
                  option -> {
                     Iris.setDebug(option);
                     Minecraft.getInstance().gui.setScreen(this);
                  },
                  Component.literal("Shader debug mode toggle"),
                  Component.literal("Debug mode helps investigate problems and shows shader errors. Would you like to enable it?"),
                  Component.literal("Yes"),
                  Component.literal("No")
               )
            );
      }

      if (Minecraft.getInstance().hasControlDown() && InputConstants.isKeyDown(10)) {
         Minecraft.getInstance()
            .gui
            .setScreen(
               new ConfirmScreen(
                  option -> {
                     try {
                        Iris.getIrisConfig().setUnknown(option);
                     } catch (IOException e) {
                        throw new RuntimeException(e);
                     }

                     Minecraft.getInstance().gui.setScreen(this);
                  },
                  Component.literal("Unknown shader toggle"),
                  Component.literal("This allows unknown shaders to load in."),
                  Component.literal("Enable"),
                  Component.literal("Disable")
               )
            );
      }

      if (!this.guiHidden) {
         super.extractRenderState(guiGraphics, mouseX, mouseY, delta);
      } else {
         this.showHideButton.extractRenderState(guiGraphics, mouseX, mouseY, delta);
      }

      float previousHoverTimer = this.guiButtonHoverTimer;
      if (previousHoverTimer == this.guiButtonHoverTimer) {
         this.guiButtonHoverTimer = 0.0F;
      }

      if (!this.guiHidden) {
         guiGraphics.centeredText(this.font, this.title, (int)(this.width * 0.5), 8, -1);
         if (this.notificationDialog != null && this.notificationDialogTimer > 0) {
            guiGraphics.centeredText(this.font, this.notificationDialog, (int)(this.width * 0.5), 21, -1);
         } else if (this.optionMenuOpen) {
            guiGraphics.centeredText(this.font, CONFIGURE_TITLE, (int)(this.width * 0.5), 21, -1);
         } else {
            guiGraphics.centeredText(this.font, SELECT_TITLE, (int)(this.width * 0.5), 21, -1);
         }

         if (this.isDisplayingComment()) {
            int panelHeight = Math.max(50, 18 + this.hoveredElementCommentBody.size() * 10);
            int x = (int)(0.5 * this.width) - 157;
            int y = this.height - (panelHeight + 4);
            GuiUtil.drawPanel(guiGraphics, x, y, 314, panelHeight);
            guiGraphics.text(this.font, this.hoveredElementCommentTitle.orElse(Component.empty()), x + 4, y + 4, -1);

            for (int i = 0; i < this.hoveredElementCommentBody.size(); i++) {
               guiGraphics.text(this.font, this.hoveredElementCommentBody.get(i), x + 4, y + 16 + i * 10, -1);
            }
         }
      }

      for (Runnable render : TOP_LAYER_RENDER_QUEUE) {
         render.run();
      }

      TOP_LAYER_RENDER_QUEUE.clear();
      if (this.developmentComponent != null) {
         guiGraphics.text(this.font, this.developmentComponent, 2, this.height - 10, -1);
         guiGraphics.text(this.font, this.irisTextComponent, 2, this.height - 20, -1);
      } else if (this.updateComponent != null) {
         guiGraphics.text(this.font, this.updateComponent, 2, this.height - 10, -1);
         guiGraphics.text(this.font, this.irisTextComponent, 2, this.height - 20, -1);
      } else {
         guiGraphics.text(this.font, this.irisTextComponent, 2, this.height - 10, -1);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean bl2) {
      int widthValue = this.font.width("New update available!");
      double x = event.x();
      double y = event.y();
      if (this.updateComponent != null && x < widthValue && y > this.height - 10 && y < this.height) {
         this.minecraft.gui.setScreen(new ConfirmLinkScreen(bl -> {
            if (bl) {
               Iris.getUpdateChecker().getUpdateLink().ifPresent(Blaze3D::openUri);
            }

            this.minecraft.gui.setScreen(this);
         }, Iris.getUpdateChecker().getUpdateLink().orElse(null), true));
      }

      return super.mouseClicked(event, bl2);
   }

   protected void init() {
      super.init();
      int bottomCenter = this.width / 2 - 50;
      int topCenter = this.width / 2 - 76;
      boolean inWorld = this.minecraft.level != null;
      this.removeWidget(this.shaderPackList);
      this.removeWidget(this.shaderOptionList);
      this.shaderPackList = new ShaderPackSelectionList(this, this.minecraft, this.width, this.height, 32, this.height - 58 - 36, 0, this.width);
      if (Iris.getCurrentPack().isPresent() && this.navigation != null) {
         ShaderPack currentPack = Iris.getCurrentPack().get();
         this.shaderOptionList = new ShaderPackOptionList(
            this, this.navigation, currentPack, this.minecraft, this.width, this.height, 32, this.height - 58 - 36, 0, this.width
         );
         this.navigation.setActiveOptionList(this.shaderOptionList);
         this.shaderOptionList.rebuild();
      } else {
         this.optionMenuOpen = false;
         this.shaderOptionList = null;
      }

      this.clearWidgets();
      if (!this.guiHidden) {
         if (this.optionMenuOpen && this.shaderOptionList != null) {
            this.addRenderableWidget(this.shaderOptionList);
         } else {
            this.addRenderableWidget(this.shaderPackList);
         }

         this.addRenderableWidget(
            IrisButton.iris$builder(CommonComponents.GUI_DONE, button -> this.onClose(), this.buttonTransition)
               .bounds(bottomCenter + 104, this.height - 27, 100, 20)
               .build()
         );
         this.addRenderableWidget(
            IrisButton.iris$builder(Component.translatable("options.iris.apply"), button -> this.applyChanges(), this.buttonTransition)
               .bounds(bottomCenter, this.height - 27, 100, 20)
               .build()
         );
         this.addRenderableWidget(
            IrisButton.iris$builder(CommonComponents.GUI_CANCEL, button -> this.dropChangesAndClose(), this.buttonTransition)
               .bounds(bottomCenter - 104, this.height - 27, 100, 20)
               .build()
         );
         this.openFolderButton = IrisButton.iris$builder(
               Component.translatable("options.iris.openShaderPackFolder"), button -> this.openShaderPackFolder(), this.buttonTransition
            )
            .bounds(topCenter - 78, this.height - 51, 152, 20)
            .build();
         this.addRenderableWidget(this.openFolderButton);
         this.screenSwitchButton = (Button)this.addRenderableWidget(IrisButton.iris$builder(Component.translatable("options.iris.shaderPackList"), button -> {
            this.optionMenuOpen = !this.optionMenuOpen;
            this.applyChanges();
            this.setFocused(this.shaderPackList.getFocused());
            this.init();
         }, this.buttonTransition).bounds(topCenter + 78, this.height - 51, 152, 20).build());
         this.refreshScreenSwitchButton();
      }

      if (inWorld) {
         Component showOrHide = this.guiHidden ? Component.translatable("options.iris.gui.show") : Component.translatable("options.iris.gui.hide");
         float endOfLastButton = this.width / 2.0F + 154.0F;
         float freeSpace = this.width - endOfLastButton;
         int x;
         if (freeSpace > 100.0F) {
            x = this.width - 50;
         } else if (freeSpace < 20.0F) {
            x = this.width - 20;
         } else {
            x = (int)(endOfLastButton + freeSpace / 2.0F) - 10;
         }

         this.showHideButton = new OldImageButton(
            x, this.height - 39, 20, 20, this.guiHidden ? 20 : 0, 146, 20, GuiUtil.IRIS_WIDGETS_TEX, 256, 256, button -> {
               this.guiHidden = !this.guiHidden;
               this.init();
            }, showOrHide
         );
         this.showHideButton.setTooltip(Tooltip.create(showOrHide));
         this.showHideButton.setTooltipDelay(Duration.ofSeconds(10L));
         this.addRenderableWidget(this.showHideButton);
      }

      this.hoveredElement = null;
      this.hoveredElementCommentTimer = 0;
   }

   public void refreshForChangedPack() {
      if (Iris.getCurrentPack().isPresent()) {
         ShaderPack currentPack = Iris.getCurrentPack().get();
         this.navigation = new NavigationController(currentPack.getMenuContainer());
         if (this.shaderOptionList != null) {
            this.shaderOptionList.applyShaderPack(currentPack);
            this.shaderOptionList.rebuild();
         }
      } else {
         this.navigation = null;
      }

      this.refreshScreenSwitchButton();
   }

   public void refreshScreenSwitchButton() {
      if (this.screenSwitchButton != null) {
         this.screenSwitchButton
            .setMessage(this.optionMenuOpen ? Component.translatable("options.iris.shaderPackList") : Component.translatable("options.iris.shaderPackSettings"));
         this.screenSwitchButton.active = this.optionMenuOpen
            || this.shaderPackList.getTopButtonRow().shadersEnabled
               && Iris.getCurrentPack().map(p -> !p.getMenuContainer().mainScreen.elements.isEmpty()).orElse(true);
      }
   }

   public void tick() {
      super.tick();
      if (this.notificationDialogTimer > 0) {
         this.notificationDialogTimer--;
      }

      if (this.hoveredElement != null) {
         this.hoveredElementCommentTimer++;
      } else {
         this.hoveredElementCommentTimer = 0;
      }
   }

   public boolean keyPressed(KeyEvent event) {
      if (event.isEscape()) {
         if (this.guiHidden) {
            this.guiHidden = false;
            this.init();
            return true;
         }

         if (this.navigation != null && this.navigation.hasHistory()) {
            this.navigation.back();
            return true;
         }

         if (this.optionMenuOpen) {
            this.optionMenuOpen = false;
            this.init();
            return true;
         }
      } else if (event.isCycleFocus()) {
         if (!this.optionMenuOpen) {
            this.shaderPackList.keyPressed(new KeyEvent(40, 0, 0));
         }

         this.optionMenuOpen = !this.optionMenuOpen;
         this.applyChanges();
         this.init();
         this.setFocused(null);
      } else if (event.key() == 58 && this.showHideButton != null) {
         this.guiHidden = !this.guiHidden;
         this.init();
      }

      return this.guiHidden || super.keyPressed(event);
   }

   public void onFilesDrop(List<Path> paths) {
      if (this.optionMenuOpen) {
         this.onOptionMenuFilesDrop(paths);
      } else {
         this.onPackListFilesDrop(paths);
      }
   }

   public void onPackListFilesDrop(List<Path> paths) {
      List<Path> packs = paths.stream().filter(Iris::isValidShaderpack).toList();

      for (Path pack : packs) {
         String fileName = pack.getFileName().toString();

         try {
            Iris.getShaderpacksDirectoryManager().copyPackIntoDirectory(fileName, pack);
         } catch (FileAlreadyExistsException e) {
            this.notificationDialog = Component.translatable("options.iris.shaderPackSelection.copyErrorAlreadyExists", new Object[]{fileName})
               .withStyle(new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.RED});
            this.notificationDialogTimer = 100;
            this.shaderPackList.refresh();
            return;
         } catch (IOException e) {
            Iris.logger.warn("Error copying dragged shader pack", e);
            this.notificationDialog = Component.translatable("options.iris.shaderPackSelection.copyError", new Object[]{fileName})
               .withStyle(new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.RED});
            this.notificationDialogTimer = 100;
            this.shaderPackList.refresh();
            return;
         }
      }

      this.shaderPackList.refresh();
      if (packs.isEmpty()) {
         if (paths.size() == 1) {
            String fileName = paths.getFirst().getFileName().toString();
            this.notificationDialog = Component.translatable("options.iris.shaderPackSelection.failedAddSingle", new Object[]{fileName})
               .withStyle(new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.RED});
         } else {
            this.notificationDialog = Component.translatable("options.iris.shaderPackSelection.failedAdd")
               .withStyle(new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.RED});
         }
      } else if (packs.size() == 1) {
         String packName = packs.getFirst().getFileName().toString();
         this.notificationDialog = Component.translatable("options.iris.shaderPackSelection.addedPack", new Object[]{packName})
            .withStyle(new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.YELLOW});
         this.shaderPackList.select(packName);
      } else {
         this.notificationDialog = Component.translatable("options.iris.shaderPackSelection.addedPacks", new Object[]{packs.size()})
            .withStyle(new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.YELLOW});
      }

      this.notificationDialogTimer = 100;
   }

   public void displayNotification(Component component) {
      this.notificationDialog = component;
      this.notificationDialogTimer = 100;
   }

   public void onOptionMenuFilesDrop(List<Path> paths) {
      if (paths.size() != 1) {
         this.notificationDialog = Component.translatable("options.iris.shaderPackOptions.tooManyFiles")
            .withStyle(new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.RED});
         this.notificationDialogTimer = 100;
      } else {
         this.importPackOptions(paths.getFirst());
      }
   }

   public void importPackOptions(Path settingFile) {
      try (InputStream in = Files.newInputStream(settingFile)) {
         Properties properties = new Properties();
         properties.load(in);
         Iris.queueShaderPackOptionsFromProperties(properties);
         this.notificationDialog = Component.translatable("options.iris.shaderPackOptions.importedSettings", new Object[]{settingFile.getFileName().toString()})
            .withStyle(new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.YELLOW});
         this.notificationDialogTimer = 100;
         if (this.navigation != null) {
            this.navigation.refresh();
         }
      } catch (Exception e) {
         Iris.logger.error("Error importing shader settings file \"" + settingFile.toString() + "\"", e);
         this.notificationDialog = Component.translatable("options.iris.shaderPackOptions.failedImport", new Object[]{settingFile.getFileName().toString()})
            .withStyle(new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.RED});
         this.notificationDialogTimer = 100;
      }
   }

   public void onClose() {
      if (!this.dropChanges) {
         this.applyChanges();
      } else {
         this.discardChanges();
      }

      try {
         this.shaderPackList.close();
      } catch (IOException e) {
         Iris.logger.error("Failed to safely close shaderpack selection!", e);
      }

      this.minecraft.gui.setScreen(this.parent);
   }

   private void dropChangesAndClose() {
      this.dropChanges = true;
      this.onClose();
   }

   public void applyChanges() {
      ShaderPackSelectionList.BaseEntry base = (ShaderPackSelectionList.BaseEntry)this.shaderPackList.getSelected();
      boolean enabled = this.shaderPackList.getTopButtonRow().shadersEnabled;
      boolean previousShadersEnabled = Iris.getIrisConfig().areShadersEnabled();
      if (enabled != previousShadersEnabled) {
         IrisApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
      }

      if (base instanceof ShaderPackSelectionList.ShaderPackEntry entry) {
         this.shaderPackList.setApplied(entry);
         String name = entry.getPackName();
         if (!name.equals(Iris.getCurrentPackName())) {
            Iris.clearShaderPackOptionQueue();
         }

         String previousPackName = Iris.getIrisConfig().getShaderPackName().orElse(null);
         if (!name.equals(previousPackName) || !Iris.getShaderPackOptionQueue().isEmpty() || Iris.shouldResetShaderPackOptionsOnNextReload()) {
            Iris.getIrisConfig().setShaderPackName(name);
            IrisApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
         }

         this.refreshForChangedPack();
      }
   }

   private void discardChanges() {
      Iris.clearShaderPackOptionQueue();
   }

   private void openShaderPackFolder() {
      CompletableFuture.runAsync(() -> Blaze3D.openUri(Iris.getShaderpacksDirectoryManager().getDirectoryUri()));
   }

   public void setElementHoveredStatus(AbstractElementWidget<?> widget, boolean hovered) {
      if (hovered && widget != this.hoveredElement) {
         this.hoveredElement = widget;
         if (widget instanceof CommentedElementWidget) {
            this.hoveredElementCommentTitle = ((CommentedElementWidget)widget).getCommentTitle();
            Optional<Component> commentBody = ((CommentedElementWidget)widget).getCommentBody();
            if (commentBody.isEmpty()) {
               this.hoveredElementCommentBody.clear();
            } else {
               String rawCommentBody = commentBody.get().getString();
               if (rawCommentBody.endsWith(".")) {
                  rawCommentBody = rawCommentBody.substring(0, rawCommentBody.length() - 1);
               }

               List<MutableComponent> splitByPeriods = Arrays.stream(rawCommentBody.split("\\. [ ]*")).<MutableComponent>map(Component::literal).toList();
               this.hoveredElementCommentBody = new ArrayList<>();

               for (MutableComponent text : splitByPeriods) {
                  this.hoveredElementCommentBody.addAll(this.font.split(text, 306));
               }
            }
         } else {
            this.hoveredElementCommentTitle = Optional.empty();
            this.hoveredElementCommentBody.clear();
         }

         this.hoveredElementCommentTimer = 0;
      } else if (!hovered && widget == this.hoveredElement) {
         this.hoveredElement = null;
         this.hoveredElementCommentTitle = Optional.empty();
         this.hoveredElementCommentBody.clear();
         this.hoveredElementCommentTimer = 0;
      }
   }

   public boolean isDisplayingComment() {
      return this.hoveredElementCommentTimer > 10 && this.hoveredElementCommentTitle.isPresent() && !this.hoveredElementCommentBody.isEmpty();
   }

   public Button getBottomRowOption() {
      return this.openFolderButton;
   }
}
