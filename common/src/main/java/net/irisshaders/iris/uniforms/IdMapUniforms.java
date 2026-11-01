package net.irisshaders.iris.uniforms;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.irisshaders.iris.api.v0.item.IrisItemLightProvider;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public final class IdMapUniforms {
   private IdMapUniforms() {
   }

   public static void addIdMapUniforms(FrameUpdateNotifier notifier, UniformHolder uniforms, IdMap idMap, boolean isOldHandLight) {
      IdMapUniforms.HeldItemSupplier mainHandSupplier = new IdMapUniforms.HeldItemSupplier(InteractionHand.MAIN_HAND, idMap.getItemIdMap(), isOldHandLight);
      IdMapUniforms.HeldItemSupplier offHandSupplier = new IdMapUniforms.HeldItemSupplier(InteractionHand.OFF_HAND, idMap.getItemIdMap(), false);
      notifier.addListener(mainHandSupplier::update);
      notifier.addListener(offHandSupplier::update);
      uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "heldItemId", mainHandSupplier::getIntID)
         .uniform1i(UniformUpdateFrequency.PER_FRAME, "heldItemId2", offHandSupplier::getIntID)
         .uniform1i(UniformUpdateFrequency.PER_FRAME, "heldBlockLightValue", mainHandSupplier::getLightValue)
         .uniform1i(UniformUpdateFrequency.PER_FRAME, "heldBlockLightValue2", offHandSupplier::getLightValue)
         .uniform3f(UniformUpdateFrequency.PER_FRAME, "heldBlockLightColor", mainHandSupplier::getLightColor)
         .uniform3f(UniformUpdateFrequency.PER_FRAME, "heldBlockLightColor2", offHandSupplier::getLightColor);
   }

   private static class HeldItemSupplier {
      private final InteractionHand hand;
      private final Object2IntFunction<NamespacedId> itemIdMap;
      private final boolean applyOldHandLight;
      private int intID;
      private int lightValue;
      private Vector3f lightColor;

      HeldItemSupplier(InteractionHand hand, Object2IntFunction<NamespacedId> itemIdMap, boolean shouldApplyOldHandLight) {
         this.hand = hand;
         this.itemIdMap = itemIdMap;
         this.applyOldHandLight = shouldApplyOldHandLight && hand == InteractionHand.MAIN_HAND;
      }

      private void invalidate() {
         this.intID = -1;
         this.lightValue = 0;
         this.lightColor = IrisItemLightProvider.DEFAULT_LIGHT_COLOR;
      }

      public void update() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player == null) {
            this.invalidate();
         } else {
            ItemStack heldStack = player.getItemInHand(this.hand);
            if (heldStack == null) {
               this.invalidate();
            } else {
               Item heldItem = heldStack.getItem();
               if (heldItem == null) {
                  this.invalidate();
               } else {
                  Identifier heldItemId = (Identifier)heldStack.get(DataComponents.ITEM_MODEL);
                  if (heldItemId == null) {
                     heldItemId = BuiltInRegistries.ITEM.getKey(heldItem);
                  }

                  this.intID = this.itemIdMap.applyAsInt(new NamespacedId(heldItemId.getNamespace(), heldItemId.getPath()));
                  IrisItemLightProvider lightProvider = (IrisItemLightProvider)heldItem;
                  this.lightValue = lightProvider.getLightEmission(Minecraft.getInstance().player, heldStack);
                  if (this.applyOldHandLight) {
                     lightProvider = this.applyOldHandLighting(player, lightProvider);
                  }

                  this.lightColor = lightProvider.getLightColor(Minecraft.getInstance().player, heldStack);
               }
            }
         }
      }

      private IrisItemLightProvider applyOldHandLighting(@NotNull LocalPlayer player, IrisItemLightProvider existing) {
         ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);
         if (offHandStack == null) {
            return existing;
         } else {
            Item offHandItem = offHandStack.getItem();
            if (offHandItem == null) {
               return existing;
            } else {
               IrisItemLightProvider lightProvider = (IrisItemLightProvider)offHandItem;
               int newEmission = lightProvider.getLightEmission(Minecraft.getInstance().player, offHandStack);
               if (this.lightValue < newEmission) {
                  this.lightValue = newEmission;
                  return lightProvider;
               } else {
                  return existing;
               }
            }
         }
      }

      public int getIntID() {
         return this.intID;
      }

      public int getLightValue() {
         return this.lightValue;
      }

      public Vector3f getLightColor() {
         return this.lightColor;
      }
   }
}
