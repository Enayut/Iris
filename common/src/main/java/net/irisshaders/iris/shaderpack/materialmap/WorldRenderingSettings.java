package net.irisshaders.iris.shaderpack.materialmap;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class WorldRenderingSettings {
   public static final WorldRenderingSettings INSTANCE = new WorldRenderingSettings();
   private boolean reloadRequired;
   private Object2IntMap<BlockState> blockStateIds;
   private Map<Block, BlockRenderType> blockTypeIds;
   private Object2IntFunction<NamespacedId> entityIds;
   private Object2IntFunction<NamespacedId> itemIds;
   private float ambientOcclusionLevel;
   private boolean disableDirectionalShading;
   private boolean hasVillagerConversionId;
   private boolean useSeparateAo;
   private boolean separateEntityDraws;
   private boolean voxelizeLightBlocks;
   private ChunkVertexType chunkVertexFormat;
   private boolean breaksAnisotropy = false;

   public WorldRenderingSettings() {
      this.reloadRequired = false;
      this.blockStateIds = null;
      this.blockTypeIds = null;
      this.ambientOcclusionLevel = 1.0F;
      this.disableDirectionalShading = false;
      this.useSeparateAo = false;
      this.chunkVertexFormat = ChunkMeshFormats.COMPACT;
      this.separateEntityDraws = false;
      this.voxelizeLightBlocks = false;
      this.hasVillagerConversionId = false;
   }

   public boolean isReloadRequired() {
      return this.reloadRequired;
   }

   public void clearReloadRequired() {
      this.reloadRequired = false;
   }

   @Nullable
   public Object2IntMap<BlockState> getBlockStateIds() {
      return this.blockStateIds;
   }

   public void setBlockStateIds(Object2IntMap<BlockState> blockStateIds) {
      if (this.blockStateIds == null || !this.blockStateIds.equals(blockStateIds)) {
         this.reloadRequired = true;
         this.blockStateIds = blockStateIds;
      }
   }

   public Map<Block, BlockRenderType> getBlockTypeIds() {
      return this.blockTypeIds;
   }

   public void setBlockTypeIds(Map<Block, BlockRenderType> blockTypeIds) {
      if (this.blockTypeIds == null || !this.blockTypeIds.equals(blockTypeIds)) {
         this.reloadRequired = true;
         this.blockTypeIds = blockTypeIds;
      }
   }

   @Nullable
   public Object2IntFunction<NamespacedId> getEntityIds() {
      return this.entityIds;
   }

   public void setEntityIds(Object2IntFunction<NamespacedId> entityIds) {
      this.entityIds = entityIds;
      this.hasVillagerConversionId = entityIds.containsKey(new NamespacedId("minecraft", "zombie_villager_converting"));
   }

   @Nullable
   public Object2IntFunction<NamespacedId> getItemIds() {
      return this.itemIds;
   }

   public void setItemIds(Object2IntFunction<NamespacedId> itemIds) {
      this.itemIds = itemIds;
   }

   public float getAmbientOcclusionLevel() {
      return this.ambientOcclusionLevel;
   }

   public void setAmbientOcclusionLevel(float ambientOcclusionLevel) {
      if (ambientOcclusionLevel != this.ambientOcclusionLevel) {
         this.reloadRequired = true;
         this.ambientOcclusionLevel = ambientOcclusionLevel;
      }
   }

   public boolean shouldDisableDirectionalShading() {
      return this.disableDirectionalShading;
   }

   public void setDisableDirectionalShading(boolean disableDirectionalShading) {
      if (disableDirectionalShading != this.disableDirectionalShading) {
         this.reloadRequired = true;
         this.disableDirectionalShading = disableDirectionalShading;
      }
   }

   public boolean shouldUseSeparateAo() {
      return this.useSeparateAo;
   }

   public void setUseSeparateAo(boolean useSeparateAo) {
      if (useSeparateAo != this.useSeparateAo) {
         this.reloadRequired = true;
         this.useSeparateAo = useSeparateAo;
      }
   }

   public ChunkVertexType getVertexFormat() {
      return this.chunkVertexFormat;
   }

   public void setVertexFormat(ChunkVertexType chunkVertexFormat) {
      if (chunkVertexFormat != this.chunkVertexFormat) {
         this.reloadRequired = true;
         this.chunkVertexFormat = chunkVertexFormat;
      }
   }

   public boolean shouldVoxelizeLightBlocks() {
      return this.voxelizeLightBlocks;
   }

   public void setVoxelizeLightBlocks(boolean voxelizeLightBlocks) {
      if (voxelizeLightBlocks != this.voxelizeLightBlocks) {
         this.reloadRequired = true;
         this.voxelizeLightBlocks = voxelizeLightBlocks;
      }
   }

   public boolean shouldSeparateEntityDraws() {
      return this.separateEntityDraws;
   }

   public void setSeparateEntityDraws(boolean separateEntityDraws) {
      this.separateEntityDraws = separateEntityDraws;
   }

   public boolean hasVillagerConversionId() {
      return this.hasVillagerConversionId;
   }

   public void setBreaksAnisotropy(boolean b) {
      this.breaksAnisotropy = b;
   }

   public boolean breaksAnisotropy() {
      return this.breaksAnisotropy;
   }
}
