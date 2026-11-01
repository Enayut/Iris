package net.irisshaders.iris;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.irisshaders.iris.compat.sodium.config.ShaderPackScreenPlaceholder;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.vulkan.IrisVulkanCapabilities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;

public class IrisVKOnly {
   public static final Category irisKeybindCategory = Category.register(Identifier.fromNamespaceAndPath("iris", "keybinds"));
   private static KeyMapping shaderpackScreenKeybind;

   public static void run() {
      shaderpackScreenKeybind = IrisPlatformHelpers.getInstance()
         .registerKeyBinding(new KeyMapping("iris.keybind.shaderPackSelection", Type.KEYBOARD, 12, irisKeybindCategory));
   }

   public static void handleKeybinds() {
      IrisVulkanCapabilities.tick();
      if (shaderpackScreenKeybind != null && shaderpackScreenKeybind.consumeClick()) {
         Minecraft.getInstance().gui.setScreen(new ShaderPackScreenPlaceholder(null));
      }
   }
}
