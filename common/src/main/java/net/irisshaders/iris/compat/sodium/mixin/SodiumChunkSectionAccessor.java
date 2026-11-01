package net.irisshaders.iris.compat.sodium.mixin;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.util.SodiumChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What Sodium captured for this frame's terrain draws, so Iris can draw single layers on Vulkan. */
@Mixin(SodiumChunkSection.class)
public interface SodiumChunkSectionAccessor {
   @Accessor("renderer")
   SodiumWorldRenderer iris$getRenderer();

   @Accessor("matrices")
   ChunkRenderMatrices iris$getMatrices();

   @Accessor("x")
   double iris$getX();

   @Accessor("y")
   double iris$getY();

   @Accessor("z")
   double iris$getZ();
}
