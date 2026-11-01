package net.irisshaders.iris.features;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.minecraft.client.resources.language.I18n;
import org.apache.commons.lang3.StringUtils;

public enum FeatureFlags {
   SEPARATE_HARDWARE_SAMPLERS(() -> true, () -> true),
   HIGHER_SHADOWCOLOR(() -> true, () -> true),
   CUSTOM_IMAGES(() -> true, IrisRenderSystem::supportsImageLoadStore),
   PER_BUFFER_BLENDING(() -> true, IrisRenderSystem::supportsBufferBlending),
   COMPUTE_SHADERS(() -> true, IrisRenderSystem::supportsCompute),
   TESSELLATION_SHADERS(() -> true, IrisRenderSystem::supportsTesselation),
   ENTITY_TRANSLUCENT(() -> true, () -> true),
   REVERSED_CULLING(() -> true, () -> true),
   BLOCK_EMISSION_ATTRIBUTE(() -> true, () -> true),
   CAN_DISABLE_WEATHER(() -> true, () -> true),
   SSBO(() -> true, IrisRenderSystem::supportsSSBO),
   FADE_VARIABLE(() -> true, () -> true),
   TEXTURE_FILTERING(() -> true, () -> true),
   UNKNOWN(() -> false, () -> false);

   private final BooleanSupplier irisRequirement;
   private final BooleanSupplier hardwareRequirement;

   FeatureFlags(BooleanSupplier irisRequirement, BooleanSupplier hardwareRequirement) {
      this.irisRequirement = irisRequirement;
      this.hardwareRequirement = hardwareRequirement;
   }

   public static String getInvalidStatus(List<FeatureFlags> invalidFeatureFlags) {
      boolean unsupportedHardware = false;
      boolean unsupportedIris = false;
      FeatureFlags[] flags = invalidFeatureFlags.toArray(new FeatureFlags[0]);

      for (FeatureFlags flag : flags) {
         unsupportedIris |= !flag.irisRequirement.getAsBoolean();
         unsupportedHardware |= !flag.hardwareRequirement.getAsBoolean();
      }

      if (unsupportedIris) {
         return unsupportedHardware ? I18n.get("iris.unsupported.irisorpc", new Object[0]) : I18n.get("iris.unsupported.iris", new Object[0]);
      } else {
         return unsupportedHardware ? I18n.get("iris.unsupported.pc", new Object[0]) : null;
      }
   }

   public static boolean isInvalid(String name) {
      try {
         return !valueOf(name.toUpperCase(Locale.US)).isUsable();
      } catch (IllegalArgumentException e) {
         return true;
      }
   }

   public static FeatureFlags getValue(String value) {
      if (value.equalsIgnoreCase("TESSELATION_SHADERS")) {
         value = "TESSELLATION_SHADERS";
      }

      try {
         return valueOf(value.toUpperCase(Locale.US));
      } catch (IllegalArgumentException e) {
         return UNKNOWN;
      }
   }

   public String getHumanReadableName() {
      return StringUtils.capitalize(this.name().replace("_", " ").toLowerCase());
   }

   public boolean isUsable() {
      return this.irisRequirement.getAsBoolean() && this.hardwareRequirement.getAsBoolean();
   }
}
