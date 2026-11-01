package net.irisshaders.iris;

import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.compat.sodium.config.ShaderPackScreenPlaceholder;
import net.irisshaders.iris.vulkan.IrisVulkanBackend;
import net.irisshaders.iris.vulkan.IrisVulkanCapabilities;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Entry points used while Minecraft runs on Vulkan, where IrisMixinPlugin only applies the VKOnly mixins.
 */
public class IrisVKOnly {
   public static final Category irisKeybindCategory = Category.register(Identifier.fromNamespaceAndPath("iris", "keybinds"));
   private static boolean packLoadAttempted;

   public static void run() {
      // Config, keybinds and the update check; none of it touches OpenGL.
      new Iris().onEarlyInitialize();
   }

   public static void handleKeybinds() {
      IrisVulkanCapabilities.tick();
      Minecraft minecraft = Minecraft.getInstance();
      if (IrisVulkanBackend.EXPERIMENTAL_RENDERING && !packLoadAttempted && RenderSystem.tryGetDevice() != null && IrisVulkanBackend.isActive()) {
         packLoadAttempted = true;
         Iris.loadShaderpack();
      }

      Iris.handleVulkanKeybinds(minecraft, () -> minecraft.gui.setScreen(new ShaderPackScreenPlaceholder(null)));
   }
}
