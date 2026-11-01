package net.irisshaders.iris.gui.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.ContainerObjectSelectionList.Entry;

public class IrisContainerObjectSelectionList<E extends Entry<E>> extends ContainerObjectSelectionList<E> {
   public IrisContainerObjectSelectionList(Minecraft client, int width, int height, int top, int bottom, int left, int right, int itemHeight) {
      super(client, width, height, top, itemHeight);
   }

   protected int scrollBarX() {
      return this.width - 6;
   }

   public void select(int entry) {
      this.setSelected(this.children().get(entry));
   }
}
