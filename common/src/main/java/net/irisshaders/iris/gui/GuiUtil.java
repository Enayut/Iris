package net.irisshaders.iris.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public final class GuiUtil {
   public static final Identifier IRIS_WIDGETS_TEX = Identifier.fromNamespaceAndPath("iris", "textures/gui/widgets.png");
   private static final Component ELLIPSIS = Component.literal("...");

   private GuiUtil() {
   }

   private static Minecraft client() {
      return Minecraft.getInstance();
   }

   public static void bindIrisWidgetsTexture() {
   }

   public static void drawButton(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, boolean hovered, boolean disabled) {
      int halfWidth = width / 2;
      int halfHeight = height / 2;
      int vOffset = disabled ? 46 : (hovered ? 86 : 66);
      guiGraphics.blit(RenderPipelines.GUI_TEXTURED, IRIS_WIDGETS_TEX, x, y, 0.0F, vOffset, halfWidth, halfHeight, 256, 256);
      guiGraphics.blit(
         RenderPipelines.GUI_TEXTURED, IRIS_WIDGETS_TEX, x + halfWidth, y, 200 - (width - halfWidth), vOffset, width - halfWidth, halfHeight, 256, 256
      );
      guiGraphics.blit(
         RenderPipelines.GUI_TEXTURED,
         IRIS_WIDGETS_TEX,
         x,
         y + halfHeight,
         0.0F,
         vOffset + (20 - (height - halfHeight)),
         halfWidth,
         height - halfHeight,
         256,
         256
      );
      guiGraphics.blit(
         RenderPipelines.GUI_TEXTURED,
         IRIS_WIDGETS_TEX,
         x + halfWidth,
         y + halfHeight,
         200 - (width - halfWidth),
         vOffset + (20 - (height - halfHeight)),
         width - halfWidth,
         height - halfHeight,
         256,
         256
      );
   }

   public static void drawPanel(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height) {
      int borderColor = -555819298;
      int innerColor = -570425344;
      guiGraphics.fill(RenderPipelines.GUI, x, y, x + width, y + 1, borderColor);
      guiGraphics.fill(RenderPipelines.GUI, x, y + height - 1, x + width, y + height, borderColor);
      guiGraphics.fill(RenderPipelines.GUI, x, y + 1, x + 1, y + height - 1, borderColor);
      guiGraphics.fill(RenderPipelines.GUI, x + width - 1, y + 1, x + width, y + height - 1, borderColor);
      guiGraphics.fill(RenderPipelines.GUI, x + 1, y + 1, x + width - 1, y + height - 1, innerColor);
   }

   public static void drawTextPanel(Font font, GuiGraphicsExtractor guiGraphics, Component text, int x, int y) {
      drawPanel(guiGraphics, x, y, font.width(text) + 8, 16);
      guiGraphics.text(font, text, x + 4, y + 4, -1);
   }

   public static MutableComponent shortenText(Font font, MutableComponent text, int width) {
      return font.width(text) > width
         ? Component.literal(font.plainSubstrByWidth(text.getString(), width - font.width(ELLIPSIS))).append(ELLIPSIS).setStyle(text.getStyle())
         : text;
   }

   public static MutableComponent translateOrDefault(MutableComponent defaultText, String translationDesc, Object... format) {
      return Language.getInstance().has(translationDesc) ? Component.translatable(translationDesc, format) : defaultText;
   }

   public static void playButtonClickSound() {
      client().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   }

   public static class Icon {
      public static final GuiUtil.Icon SEARCH = new GuiUtil.Icon(0, 0, 7, 8);
      public static final GuiUtil.Icon CLOSE = new GuiUtil.Icon(7, 0, 5, 6);
      public static final GuiUtil.Icon REFRESH = new GuiUtil.Icon(12, 0, 10, 10);
      public static final GuiUtil.Icon EXPORT = new GuiUtil.Icon(22, 0, 7, 8);
      public static final GuiUtil.Icon EXPORT_COLORED = new GuiUtil.Icon(29, 0, 7, 8);
      public static final GuiUtil.Icon IMPORT = new GuiUtil.Icon(22, 8, 7, 8);
      public static final GuiUtil.Icon IMPORT_COLORED = new GuiUtil.Icon(29, 8, 7, 8);
      private final int u;
      private final int v;
      private final int width;
      private final int height;

      public Icon(int u, int v, int width, int height) {
         this.u = u;
         this.v = v;
         this.width = width;
         this.height = height;
      }

      public void draw(GuiGraphicsExtractor guiGraphics, int x, int y) {
         guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GuiUtil.IRIS_WIDGETS_TEX, x, y, this.u, this.v, this.width, this.height, 256, 256);
      }

      public int getWidth() {
         return this.width;
      }

      public int getHeight() {
         return this.height;
      }
   }
}
