package net.irisshaders.iris.uniforms;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.mixinterface.ExtendedBiome;
import net.irisshaders.iris.parsing.BiomeCategories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.Precipitation;

public class BiomeUniforms {
   private static final Object2IntMap<ResourceKey<Biome>> biomeMap = new Object2IntOpenHashMap();

   public static Object2IntMap<ResourceKey<Biome>> getBiomeMap() {
      return biomeMap;
   }

   public static void addBiomeUniforms(UniformHolder uniforms) {
      uniforms.uniform1i(
            UniformUpdateFrequency.PER_TICK,
            "biome",
            playerI(player -> biomeMap.getInt(player.level().getBiome(player.blockPosition()).unwrapKey().orElse(null)))
         )
         .uniform1i(UniformUpdateFrequency.PER_TICK, "biome_category", playerI(player -> {
            Holder<Biome> holder = player.level().getBiome(player.blockPosition());
            ExtendedBiome extendedBiome = (ExtendedBiome)(Object)holder.value();
            if (extendedBiome.getBiomeCategory() == -1) {
               extendedBiome.setBiomeCategory(getBiomeCategory(holder).ordinal());
               return extendedBiome.getBiomeCategory();
            } else {
               return extendedBiome.getBiomeCategory();
            }
         }))
         .uniform1i(
            UniformUpdateFrequency.PER_TICK,
            "biome_precipitation",
            playerI(
               player -> {
                  Precipitation precipitation = ((Biome)player.level().getBiome(player.blockPosition()).value())
                     .getPrecipitationAt(player.blockPosition(), player.level().getSeaLevel());

                  return switch (precipitation) {
                     case NONE -> 0;
                     case RAIN -> 1;
                     case SNOW -> 2;
                     default -> throw new MatchException(null, null);
                  };
               }
            )
         )
         .uniform1f(
            UniformUpdateFrequency.PER_TICK,
            "rainfall",
            playerF(player -> ((ExtendedBiome)(Object)player.level().getBiome(player.blockPosition()).value()).getDownfall())
         )
         .uniform1f(
            UniformUpdateFrequency.PER_TICK,
            "temperature",
            playerF(player -> ((Biome)player.level().getBiome(player.blockPosition()).value()).getBaseTemperature())
         );
   }

   private static BiomeCategories getBiomeCategory(Holder<Biome> holder) {
      if (holder.is(BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS)) {
         return BiomeCategories.NONE;
      } else if (holder.is(BiomeTags.HAS_VILLAGE_SNOWY)) {
         return BiomeCategories.ICY;
      } else if (holder.is(BiomeTags.IS_HILL)) {
         return BiomeCategories.EXTREME_HILLS;
      } else if (holder.is(BiomeTags.IS_TAIGA)) {
         return BiomeCategories.TAIGA;
      } else if (holder.is(BiomeTags.IS_OCEAN)) {
         return BiomeCategories.OCEAN;
      } else if (holder.is(BiomeTags.IS_JUNGLE)) {
         return BiomeCategories.JUNGLE;
      } else if (holder.is(BiomeTags.IS_FOREST)) {
         return BiomeCategories.FOREST;
      } else if (holder.is(BiomeTags.IS_BADLANDS)) {
         return BiomeCategories.MESA;
      } else if (holder.is(BiomeTags.IS_NETHER)) {
         return BiomeCategories.NETHER;
      } else if (holder.is(BiomeTags.IS_END)) {
         return BiomeCategories.THE_END;
      } else if (holder.is(BiomeTags.IS_BEACH)) {
         return BiomeCategories.BEACH;
      } else if (holder.is(BiomeTags.HAS_DESERT_PYRAMID)) {
         return BiomeCategories.DESERT;
      } else if (holder.is(BiomeTags.IS_RIVER)) {
         return BiomeCategories.RIVER;
      } else if (holder.is(BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS)) {
         return BiomeCategories.SWAMP;
      } else if (holder.is(BiomeTags.WITHOUT_ZOMBIE_SIEGES)) {
         return BiomeCategories.MUSHROOM;
      } else {
         return holder.is(BiomeTags.IS_MOUNTAIN) ? BiomeCategories.MOUNTAIN : BiomeCategories.PLAINS;
      }
   }

   static IntSupplier playerI(ToIntFunction<LocalPlayer> function) {
      return () -> {
         LocalPlayer player = Minecraft.getInstance().player;
         return player == null ? 0 : function.applyAsInt(player);
      };
   }

   static FloatSupplier playerF(BiomeUniforms.ToFloatFunction<LocalPlayer> function) {
      return () -> {
         LocalPlayer player = Minecraft.getInstance().player;
         return player == null ? 0.0F : function.applyAsFloat(player);
      };
   }

   @FunctionalInterface
   public interface ToFloatFunction<T> {
      float applyAsFloat(T var1);
   }
}
