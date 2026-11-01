package net.irisshaders.iris.compat.sodium.config;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.backend.opengl.GlDevice;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.option.Range;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.PageBuilder;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlValueFormatterImpls;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.mixin.GpuDeviceAccessor;
import net.irisshaders.iris.mixin.IrisMixinPlugin;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import net.irisshaders.iris.vulkan.IrisVulkanCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class IrisConfig implements ConfigEntryPoint {
   public static final Identifier MONO = Identifier.fromNamespaceAndPath("iris", "textures/gui/config-icon-mono.png");
   public static final Identifier COLOR = Identifier.fromNamespaceAndPath("iris", "textures/gui/config-icon.png");

   public void registerConfigLate(ConfigBuilder builder) {
      boolean vk = IrisMixinPlugin.usingVulkan;
      ModOptionsBuilder modOptions = builder.registerOwnModOptions()
         .setName("Iris")
         .setIcon(MONO)
         .setColorTheme(builder.createColorTheme().setBaseThemeRGB(-698654))
         .setVersion(Iris.getVersionSimple())
         .addPage(
            builder.createExternalPage()
               .setName(Component.translatable("options.iris.shaderPackSelection.title"))
               .setScreenConsumer(
                  i -> Minecraft.getInstance()
                     .gui
                     .setScreen(
                        (Screen)(vk && !IrisVulkanCapabilities.isShaderpackRenderingAvailable() ? new ShaderPackScreenPlaceholder(i) : new ShaderPackScreen(i))
                     )
               )
         );
      if (!vk) {
         modOptions.addPage(this.createSettingsPage(builder));
         modOptions.registerOptionOverlay(
               Identifier.parse("sodium:quality.filtering_mode"),
               builder.createEnumOption(Identifier.parse("sodium:quality.filtering_mode"), TextureFilteringMethod.class)
                  .setTooltip(
                     i -> i == TextureFilteringMethod.RGSS
                        ? Component.translatable("options.textureFiltering." + i.name().toLowerCase(Locale.ROOT) + ".tooltip")
                           .append(Component.literal(" (RGSS is not usable with shaders on.)"))
                        : Component.translatable("options.textureFiltering." + i.name().toLowerCase(Locale.ROOT) + ".tooltip")
                  )
                  .setAllowedValuesProvider(
                     state -> Iris.getCurrentPack().isPresent()
                        ? Set.of(TextureFilteringMethod.NONE, TextureFilteringMethod.ANISOTROPIC)
                        : Set.of(TextureFilteringMethod.values()),
                     new Identifier[]{ConfigState.UPDATE_ON_REBUILD}
                  )
            )
            .registerOptionOverlay(
               Identifier.parse("sodium:quality.graphics"),
               builder.createBooleanOption(Identifier.parse("sodium:quality.graphics"))
                  .setTooltip(
                     i -> Iris.getCurrentPack().isPresent()
                        ? Component.literal("This option is not relevant when a shader pack is active.")
                        : Component.translatable("options.improvedTransparency.tooltip")
                  )
                  .setEnabledProvider(i -> Iris.getCurrentPack().isEmpty(), new Identifier[]{ConfigState.UPDATE_ON_REBUILD})
            )
            .registerOptionOverlay(
               Identifier.parse("sodium:general.graphics_api"),
               builder.createEnumOption(Identifier.fromNamespaceAndPath("sodium", "general.graphics_api"), PreferredGraphicsApi.class)
                  .setBinding(
                     value -> {
                        if (((GpuDeviceAccessor)RenderSystem.getDevice()).getBackend() instanceof GlDevice && value == PreferredGraphicsApi.VULKAN) {
                           Screen s = Minecraft.getInstance().gui.screen();
                           Minecraft.getInstance()
                              .gui
                              .setScreen(
                                 new ConfirmScreen(
                                    i -> {
                                       if (i) {
                                          Minecraft.getInstance().options.preferredGraphicsBackend().set(value);
                                          Minecraft.getInstance().gui.setScreen(s);
                                       } else {
                                          s.onClose();
                                          Minecraft.getInstance().gui.setScreen(VideoSettingsScreen.createScreen(Minecraft.getInstance().gui.screen()));
                                       }
                                    },
                                    Component.literal("Iris"),
                                    Component.literal("Setting the backend to Vulkan will remove your ability to use shaders.\nWould you like to proceed?")
                                 )
                              );
                        } else {
                           Minecraft.getInstance().options.preferredGraphicsBackend().set(value);
                        }
                     },
                     () -> (PreferredGraphicsApi)Minecraft.getInstance().options.preferredGraphicsBackend().get()
                  )
            );
      }
   }

   private PageBuilder createSettingsPage(ConfigBuilder builder) {
      return builder.createOptionPage()
         .setName(Component.literal("Settings"))
         .addOptionGroup(
            builder.createOptionGroup()
               .addOption(
                  builder.createExternalButtonOption(Identifier.fromNamespaceAndPath("iris", "settings"))
                     .setTooltip(Component.literal("Packs"))
                     .setName(Component.translatable("options.iris.shaderPackList"))
                     .setScreenConsumer(i -> Minecraft.getInstance().gui.setScreen(new ShaderPackScreen(i)))
               )
         )
         .addOptionGroup(builder.createOptionGroup().addOption(this.createColorSpaceOption(builder)).addOption(this.createShadowDistanceOption(builder)));
   }

   private OptionBuilder createColorSpaceOption(ConfigBuilder builder) {
      return builder.createEnumOption(Identifier.fromNamespaceAndPath("iris", "color_space"), ColorSpace.class)
         .setBinding(value -> IrisVideoSettings.colorSpace = value, () -> IrisVideoSettings.colorSpace)
         .setName(Component.translatable("options.iris.colorSpace"))
         .setDefaultValue(ColorSpace.SRGB)
         .setTooltip(Component.translatable("options.iris.colorSpace.sodium_tooltip"))
         .setStorageHandler(IrisConfig::saveConfig)
         .setElementNameProvider(ColorSpace::getName);
   }

   private OptionBuilder createShadowDistanceOption(ConfigBuilder builder) {
      return builder.createIntegerOption(Identifier.fromNamespaceAndPath("iris", "shadow_distance"))
         .setDefaultValue(32)
         .setBinding(value -> IrisVideoSettings.shadowDistance = value, () -> IrisVideoSettings.getOverriddenShadowDistance(IrisVideoSettings.shadowDistance))
         .setName(Component.translatable("options.iris.shadowDistance"))
         .setTooltip(
            i -> !IrisVideoSettings.isShadowDistanceSliderEnabled()
               ? Component.translatable("options.iris.shadowDistance.disabled")
               : Component.translatable("options.iris.shadowDistance.sodium_tooltip")
         )
         .setValueFormatter(
            ControlValueFormatterImpls.quantityOrDisabled(i -> Component.translatable("options.chunks", new Object[]{i}), Component.literal("None"))
         )
         .setEnabledProvider(i -> IrisVideoSettings.isShadowDistanceSliderEnabled(), new Identifier[]{ConfigState.UPDATE_ON_REBUILD})
         .setStorageHandler(IrisConfig::saveConfig)
         .setRange(new Range(0, 32, 1))
         .setImpact(OptionImpact.HIGH);
   }

   private static void saveConfig() {
      try {
         Iris.getIrisConfig().save();
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }
}
