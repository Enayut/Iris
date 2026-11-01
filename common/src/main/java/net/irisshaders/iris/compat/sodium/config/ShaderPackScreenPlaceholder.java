package net.irisshaders.iris.compat.sodium.config;

import java.util.List;
import java.util.stream.Collectors;
import net.irisshaders.iris.vulkan.IrisVulkanCapabilities;
import net.irisshaders.iris.vulkan.IrisVulkanProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shown instead of the shader pack screen while Vulkan is the active backend and
 * {@link IrisVulkanCapabilities#isShaderpackRenderingAvailable()} is false. It reports what the Vulkan
 * capability check found rather than a blanket refusal, and still offers to switch to OpenGL.
 */
public class ShaderPackScreenPlaceholder extends Screen {
   private Screen parent;
   private MultiLineLabel message;
   private String messageText;
   private Component confirmation = Component.literal("Switch");

   public ShaderPackScreenPlaceholder(Screen i) {
      super(Component.literal("Iris"));
      this.parent = i;
   }

   protected void init() {
      super.init();
      IrisVulkanProbe.get().start();
      this.messageText = buildMessage();
      this.message = MultiLineLabel.create(this.font, Component.literal(this.messageText), this.width - 50);
      int textSize = (this.message.getLineCount() + 1) * 9;
      this.addRenderableWidget(Button.builder(this.confirmation, this::switchToOpenGl).bounds(this.width / 2 - 155, 70 + textSize, 150, 20).build());
      Button skipAndJoinButton = Button.builder(Component.literal("Return"), i -> this.onClose())
         .bounds(this.width / 2 - 155 + 160, 70 + textSize, 150, 20)
         .build();
      this.addRenderableWidget(skipAndJoinButton);
   }

   @Override
   public void tick() {
      super.tick();
      // The probe finishes asynchronously; re-layout once its status changes.
      if (!buildMessage().equals(this.messageText)) {
         this.rebuildWidgets();
      }
   }

   private static String buildMessage() {
      List<IrisVulkanCapabilities.Check> checks = IrisVulkanCapabilities.evaluate();
      StringBuilder text = new StringBuilder("Iris shader packs cannot run on the Vulkan backend yet.\n\n");

      for (IrisVulkanCapabilities.Check check : checks) {
         if (check.status() != IrisVulkanCapabilities.Status.MISSING) {
            text.append(check.name()).append(": ").append(check.status()).append(" - ").append(check.detail()).append('\n');
         }
      }

      text.append("Not yet implemented for Vulkan: ")
         .append(checks.stream()
            .filter(check -> check.status() == IrisVulkanCapabilities.Status.MISSING)
            .map(IrisVulkanCapabilities.Check::name)
            .collect(Collectors.joining(", ")))
         .append(". Full report in logs/latest.log.\n\n");
      text.append("Would you like to switch to OpenGL to use shader packs? This will close your game.");
      return text.toString();
   }

   private void switchToOpenGl(Button button) {
      Minecraft.getInstance().options.preferredGraphicsBackend().set(PreferredGraphicsApi.OPENGL);
      Minecraft.getInstance().options.save();
      if (Minecraft.getInstance().isLocalServer() && Minecraft.getInstance().getSingleplayerServer() != null) {
         Minecraft.getInstance().getSingleplayerServer().halt(true);
      }

      Minecraft.getInstance().disconnectWithSavingScreen();
      Minecraft.getInstance().stop();
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      super.extractRenderState(graphics, mouseX, mouseY, a);
      ActiveTextCollector textRenderer = graphics.textRenderer();
      graphics.centeredText(this.font, this.title, this.width / 2, 30, -1);
      this.message.visitLines(TextAlignment.CENTER, this.width / 2, 50, 9, textRenderer);
   }

   public void onClose() {
      Minecraft.getInstance().gui.setScreen(this.parent);
   }
}
