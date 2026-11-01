package net.irisshaders.iris.mixinterface;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public interface ItemContextState {
   void setDisplayItem(Item var1, Identifier var2);

   Item getDisplayItem();

   Identifier getDisplayItemModel();
}
