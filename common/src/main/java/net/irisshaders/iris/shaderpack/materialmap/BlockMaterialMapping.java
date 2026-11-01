package net.irisshaders.iris.shaderpack.materialmap;

import com.google.common.collect.UnmodifiableIterator;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockMaterialMapping {
   public static Object2IntMap<BlockState> createBlockStateIdMap(
      Int2ObjectLinkedOpenHashMap<List<BlockEntry>> blockPropertiesMap, Int2ObjectLinkedOpenHashMap<List<TagEntry>> tagPropertiesMap
   ) {
      Object2IntMap<BlockState> blockStateIds = new Object2IntLinkedOpenHashMap();
      blockStateIds.defaultReturnValue(-1);
      blockPropertiesMap.forEach((intId, entries) -> {
         for (BlockEntry entry : entries) {
            addBlockStates(entry, blockStateIds, intId);
         }
      });
      tagPropertiesMap.forEach((intId, entries) -> {
         for (TagEntry entry : entries) {
            addTag(entry, blockStateIds, intId);
         }
      });
      return blockStateIds;
   }

   private static void addTag(TagEntry tagEntry, Object2IntMap<BlockState> idMap, int intId) {
      List<Named<Block>> compatibleTags = BuiltInRegistries.BLOCK
         .getTags()
         .filter(
            t -> t.key().location().getNamespace().equalsIgnoreCase(tagEntry.id().getNamespace())
               && t.key().location().getPath().equalsIgnoreCase(tagEntry.id().getName())
         )
         .toList();
      if (compatibleTags.isEmpty()) {
         if (IrisPlatformHelpers.getInstance().isDevelopmentEnvironment()) {
            Iris.logger.warn("Failed to find the tag " + tagEntry.id());
         }
      } else if (compatibleTags.size() > 1) {
         Iris.logger.fatal("You've broke the system; congrats. More than one tag matched " + tagEntry.id());
      } else {
         BuiltInRegistries.BLOCK
            .getTagOrEmpty(compatibleTags.getFirst().key())
            .forEach(
               block -> {
                  Map<String, String> propertyPredicates = tagEntry.propertyPredicates();
                  if (!propertyPredicates.isEmpty()) {
                     Map<Property<?>, String> properties = new LinkedHashMap<>();
                     StateDefinition<Block, BlockState> stateManager = ((Block)block.value()).getStateDefinition();
                     propertyPredicates.forEach(
                        (key, value) -> {
                           Property<?> property = stateManager.getProperty(key);
                           if (property == null) {
                              Iris.logger.warn("Error while parsing the block ID map entry for tag \"block." + intId + "\":");
                              Iris.logger
                                 .warn(
                                    "- The block "
                                       + ((ResourceKey)block.unwrapKey().get()).identifier()
                                       + " has no property with the name "
                                       + key
                                       + ", ignoring!"
                                 );
                           } else {
                              properties.put(property, value);
                           }
                        }
                     );
                     Iterator i$ = stateManager.getPossibleStates().iterator();

                     while (i$.hasNext()) {
                        BlockState state = (BlockState)i$.next();
                        if (checkState(state, properties)) {
                           idMap.putIfAbsent(state, intId);
                        }
                     }
                  } else {
                     Iterator i$ = ((Block)block.value()).getStateDefinition().getPossibleStates().iterator();

                     while (i$.hasNext()) {
                        BlockState state = (BlockState)i$.next();
                        idMap.putIfAbsent(state, intId);
                     }
                  }
               }
            );
      }
   }

   public static Map<Block, BlockRenderType> createBlockTypeMap(Map<NamespacedId, BlockRenderType> blockPropertiesMap) {
      if (blockPropertiesMap.isEmpty()) {
         return Object2ObjectMaps.emptyMap();
      }

      Map<Block, BlockRenderType> blockTypeIds = new Reference2ReferenceOpenHashMap();
      blockPropertiesMap.forEach((id, blockType) -> {
         Identifier identifier = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getName());
         Block block = BuiltInRegistries.BLOCK.get(identifier).<Block>map(Holder::value).orElse(Blocks.AIR);
         blockTypeIds.put(block, blockType);
      });
      return blockTypeIds;
   }

   public static ChunkSectionLayer convertBlockToRenderType(BlockRenderType type) {
      if (type == null) {
         return null;
      }

      return switch (type) {
         case SOLID -> ChunkSectionLayer.SOLID;
         case CUTOUT -> ChunkSectionLayer.CUTOUT;
         case CUTOUT_MIPPED -> ChunkSectionLayer.CUTOUT;
         case TRANSLUCENT -> ChunkSectionLayer.TRANSLUCENT;
      };
   }

   private static void addBlockStates(BlockEntry entry, Object2IntMap<BlockState> idMap, int intId) {
      NamespacedId id = entry.id();

      Identifier identifier;
      try {
         identifier = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getName());
      } catch (Exception exception) {
         throw new IllegalStateException("Failed to get entry for " + intId, exception);
      }

      Block block = BuiltInRegistries.BLOCK.get(identifier).<Block>map(Holder::value).orElse(Blocks.AIR);
      if (block != Blocks.AIR) {
         Map<String, String> propertyPredicates = entry.propertyPredicates();
         if (!propertyPredicates.isEmpty()) {
            Map<Property<?>, String> properties = new LinkedHashMap<>();
            StateDefinition<Block, BlockState> stateManager = block.getStateDefinition();
            propertyPredicates.forEach((key, value) -> {
               Property<?> property = stateManager.getProperty(key);
               if (property == null) {
                  Iris.logger.warn("Error while parsing the block ID map entry for \"block." + intId + "\":");
                  Iris.logger.warn("- The block " + identifier + " has no property with the name " + key + ", ignoring!");
               } else {
                  properties.put(property, value);
               }
            });
            UnmodifiableIterator var9 = stateManager.getPossibleStates().iterator();

            while (var9.hasNext()) {
               BlockState state = (BlockState)var9.next();
               if (checkState(state, properties)) {
                  idMap.putIfAbsent(state, intId);
               }
            }
         } else {
            UnmodifiableIterator properties = block.getStateDefinition().getPossibleStates().iterator();

            while (properties.hasNext()) {
               BlockState state = (BlockState)properties.next();
               idMap.putIfAbsent(state, intId);
            }
         }
      }
   }

   private static boolean checkState(BlockState state, Map<Property<?>, String> expectedValues) {
      for (java.util.Map.Entry<Property<?>, String> condition : expectedValues.entrySet()) {
         Property property = condition.getKey();
         String expectedValue = condition.getValue();
         String actualValue = property.getName(state.getValue(property));
         if (!expectedValue.equals(actualValue)) {
            return false;
         }
      }

      return true;
   }
}
