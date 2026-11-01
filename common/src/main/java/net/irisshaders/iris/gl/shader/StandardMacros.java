package net.irisshaders.iris.gl.shader;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.pbr.format.TextureFormat;
import net.irisshaders.iris.pbr.format.TextureFormatLoader;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.vulkan.IrisVulkanBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

public class StandardMacros {
   private static final Pattern SEMVER_PATTERN = Pattern.compile("(?<major>\\d+)\\.(?<minor>\\d+)\\.*(?<bugfix>\\d*)(.*)");

   private static void define(List<StringPair> defines, String key) {
      defines.add(new StringPair(key, ""));
   }

   private static void define(List<StringPair> defines, String key, String value) {
      defines.add(new StringPair(key, value));
   }

   // RenderPearl compiles Vulkan shaders with shaderc/glslang, which accepts GLSL up to 4.60. What the backend can
   // actually do (no compute, images, SSBOs, tessellation) is advertised through FeatureFlags, not the version.
   private static final String VULKAN_GLSL_VERSION = "460";

   public static ImmutableList<StringPair> createStandardEnvironmentDefines() {
      boolean vulkan = IrisVulkanBackend.isActive();
      ArrayList<StringPair> standardDefines = new ArrayList<>();
      define(standardDefines, "MC_VERSION", getMcVersion());
      define(standardDefines, "MC_MIPMAP_LEVEL", String.valueOf(Minecraft.getInstance().options.mipmapLevels().get()));
      define(standardDefines, "IRIS_VERSION", getFormattedIrisVersion());
      define(standardDefines, "MC_GL_VERSION", vulkan ? VULKAN_GLSL_VERSION : getGlVersion(7938));
      define(standardDefines, "MC_GLSL_VERSION", vulkan ? VULKAN_GLSL_VERSION : getGlVersion(35724));
      define(standardDefines, getOsString());
      define(standardDefines, getVendor());
      define(standardDefines, getRenderer());
      define(standardDefines, "IS_IRIS");
      define(standardDefines, "IRIS_REQUIRES_SEPARATE_ENTITY_DRAWS");
      define(standardDefines, "MAX_COLOR_BUFFERS", String.valueOf(32));
      define(standardDefines, "IRIS_HAS_TRANSLUCENCY_SORTING");
      define(standardDefines, "IRIS_INLINE_GLINT");
      define(standardDefines, "IRIS_TAG_SUPPORT", "2");
      if (IrisPlatformHelpers.getInstance().isModLoaded("distanthorizons") && DHCompat.hasRenderingEnabled()) {
         define(standardDefines, "DISTANT_HORIZONS");
         define(standardDefines, "DISTANT_HORIZONS_TEXTURES");
      }

      if (IrisPlatformHelpers.getInstance().isModLoaded("continuity")) {
         define(standardDefines, "IRIS_HAS_CONNECTED_TEXTURES");
      }

      if (IrisPlatformHelpers.getInstance().isModLoaded("monocle")) {
         define(standardDefines, "IS_MONOCLE");
      }

      if (Iris.getIrisConfig().shouldAllowUnknownShaders()) {
         define(standardDefines, "ALLOWS_UNKNOWN_SHADERS");
      }

      define(standardDefines, "DH_BLOCK_UNKNOWN", String.valueOf(0));
      define(standardDefines, "DH_BLOCK_LEAVES", String.valueOf(1));
      define(standardDefines, "DH_BLOCK_STONE", String.valueOf(2));
      define(standardDefines, "DH_BLOCK_WOOD", String.valueOf(3));
      define(standardDefines, "DH_BLOCK_METAL", String.valueOf(4));
      define(standardDefines, "DH_BLOCK_DIRT", String.valueOf(5));
      define(standardDefines, "DH_BLOCK_LAVA", String.valueOf(6));
      define(standardDefines, "DH_BLOCK_DEEPSLATE", String.valueOf(7));
      define(standardDefines, "DH_BLOCK_SNOW", String.valueOf(8));
      define(standardDefines, "DH_BLOCK_SAND", String.valueOf(9));
      define(standardDefines, "DH_BLOCK_TERRACOTTA", String.valueOf(10));
      define(standardDefines, "DH_BLOCK_NETHER_STONE", String.valueOf(11));
      define(standardDefines, "DH_BLOCK_WATER", String.valueOf(12));
      define(standardDefines, "DH_BLOCK_GRASS", String.valueOf(13));
      define(standardDefines, "DH_BLOCK_AIR", String.valueOf(14));
      define(standardDefines, "DH_BLOCK_ILLUMINATED", String.valueOf(15));

      for (String glExtension : vulkan ? Set.<String>of() : getGlExtensions()) {
         define(standardDefines, glExtension);
      }

      define(standardDefines, "MC_NORMAL_MAP");
      define(standardDefines, "MC_SPECULAR_MAP");
      define(standardDefines, "MC_RENDER_QUALITY", "1.0");
      define(standardDefines, "MC_SHADOW_QUALITY", "1.0");
      define(standardDefines, "MC_HAND_DEPTH", Float.toString(0.125F));
      TextureFormat textureFormat = TextureFormatLoader.getFormat();
      if (textureFormat != null) {
         for (String define : textureFormat.getDefines()) {
            define(standardDefines, define);
         }
      }

      getRenderStages().forEach((stage, index) -> define(standardDefines, stage, index));

      for (String irisDefine : getIrisDefines()) {
         define(standardDefines, irisDefine);
      }

      return ImmutableList.copyOf(standardDefines);
   }

   public static String getMcVersion() {
      String version = Iris.getReleaseTarget();
      if (version == null) {
         throw new IllegalStateException("Could not get the current Minecraft version!");
      } else {
         String formattedVersion = formatVersionString(version);
         if (formattedVersion == null) {
            throw new IllegalStateException("Could not parse game version \"" + version + "\"");
         } else {
            return formattedVersion;
         }
      }
   }

   public static String getFormattedIrisVersion() {
      String rawVersion = Iris.getVersion();
      if (rawVersion == null) {
         throw new IllegalArgumentException("Could not get current Iris version!");
      }

      Matcher matcher = SEMVER_PATTERN.matcher(rawVersion);
      if (!matcher.matches()) {
         throw new IllegalArgumentException("Could not parse semantic Iris version from \"" + rawVersion + "\"");
      }

      String major = matcher.group("major");
      String minor = matcher.group("minor");
      String bugFix = matcher.group("bugfix");
      if (bugFix == null) {
         bugFix = "0";
      }

      if (major != null && minor != null) {
         String irisSemver = "%s.%s.%s".formatted(major, minor, bugFix);
         String formattedSemver = formatVersionString(irisSemver);
         if (formattedSemver == null) {
            throw new IllegalArgumentException("Could not get a valid semantic version string for Iris version \"" + irisSemver + "\"");
         } else {
            return formattedSemver;
         }
      } else {
         throw new IllegalArgumentException("Could not parse semantic Iris version from \"" + rawVersion + "\"");
      }
   }

   @Nullable
   public static String formatVersionString(String version) {
      String[] splitVersion = version.split("\\.");
      if (splitVersion.length < 2) {
         return null;
      }

      String major = splitVersion[0];
      String minor = splitVersion[1].length() == 1 ? "0" + splitVersion[1] : splitVersion[1];
      String bugFix = splitVersion.length < 3 ? "00" : splitVersion[2];
      if (bugFix.length() == 1) {
         bugFix = "0" + bugFix;
      }

      return major + minor + bugFix;
   }

   public static String getGlVersion(int name) {
      String info = GlStateManager._getString(name);
      Matcher matcher = SEMVER_PATTERN.matcher(Objects.requireNonNull(info));
      if (!matcher.matches()) {
         throw new IllegalStateException("Could not parse GL version from \"" + info + "\"");
      }

      String major = group(matcher, "major");
      String minor = group(matcher, "minor");
      String bugfix = group(matcher, "bugfix");
      if (bugfix == null) {
         bugfix = "0";
      }

      if (major != null && minor != null) {
         return major + minor + bugfix;
      } else {
         throw new IllegalStateException("Could not parse GL version from \"" + info + "\"");
      }
   }

   public static String group(Matcher matcher, String name) {
      try {
         return matcher.group(name);
      } catch (IllegalArgumentException | IllegalStateException exception) {
         return null;
      }
   }

   public static String getOsString() {
      return switch (Util.getPlatform()) {
         case OSX -> "MC_OS_MAC";
         case LINUX -> "MC_OS_LINUX";
         case WINDOWS -> "MC_OS_WINDOWS";
         default -> "MC_OS_UNKNOWN";
      };
   }

   public static String getVendor() {
      String vendor = Objects.requireNonNull(RenderSystem.getDevice().getDeviceInfo().vendorName()).toLowerCase(Locale.ROOT);
      if (vendor.startsWith("ati")) {
         return "MC_GL_VENDOR_ATI";
      } else if (vendor.startsWith("intel")) {
         return "MC_GL_VENDOR_INTEL";
      } else if (vendor.startsWith("nvidia")) {
         return "MC_GL_VENDOR_NVIDIA";
      } else if (vendor.startsWith("amd")) {
         return "MC_GL_VENDOR_AMD";
      } else {
         return vendor.startsWith("x.org") ? "MC_GL_VENDOR_XORG" : "MC_GL_VENDOR_OTHER";
      }
   }

   public static String getRenderer() {
      // Under Vulkan driverInfo() is a version string ("1.4.312 NVIDIA 580..."); the device name carries the model.
      DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
      String renderer = Objects.requireNonNull(IrisVulkanBackend.isActive() ? info.name() : info.driverInfo()).toLowerCase(Locale.ROOT);
      if (renderer.startsWith("amd")) {
         return "MC_GL_RENDERER_RADEON";
      } else if (renderer.startsWith("ati")) {
         return "MC_GL_RENDERER_RADEON";
      } else if (renderer.startsWith("radeon")) {
         return "MC_GL_RENDERER_RADEON";
      } else if (renderer.startsWith("gallium")) {
         return "MC_GL_RENDERER_GALLIUM";
      } else if (renderer.startsWith("intel")) {
         return "MC_GL_RENDERER_INTEL";
      } else if (renderer.startsWith("geforce")) {
         return "MC_GL_RENDERER_GEFORCE";
      } else if (renderer.startsWith("nvidia")) {
         return "MC_GL_RENDERER_GEFORCE";
      } else if (renderer.startsWith("quadro")) {
         return "MC_GL_RENDERER_QUADRO";
      } else if (renderer.startsWith("nvs")) {
         return "MC_GL_RENDERER_QUADRO";
      } else if (renderer.startsWith("mesa")) {
         return "MC_GL_RENDERER_MESA";
      } else {
         return renderer.startsWith("apple") ? "MC_GL_RENDERER_APPLE" : "MC_GL_RENDERER_OTHER";
      }
   }

   public static Set<String> getGlExtensions() {
      int numExtensions = GlStateManager._getInteger(33309);
      String[] extensions = new String[numExtensions];

      for (int i = 0; i < numExtensions; i++) {
         extensions[i] = IrisRenderSystem.getStringi(7939, i);
      }

      return Arrays.stream(extensions).map(s -> "MC_" + s).collect(Collectors.toSet());
   }

   public static Map<String, String> getRenderStages() {
      Map<String, String> stages = new HashMap<>();

      for (WorldRenderingPhase phase : WorldRenderingPhase.values()) {
         stages.put("MC_RENDER_STAGE_" + phase.name(), String.valueOf(phase.ordinal()));
      }

      return stages;
   }

   public static List<String> getIrisDefines() {
      return new ArrayList<>();
   }
}
