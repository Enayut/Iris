package net.irisshaders.iris.gui.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractSelectionList.Entry;
import net.minecraft.client.gui.narration.NarrationElementOutput;

public class IrisObjectSelectionList<E extends Entry<E>> extends AbstractSelectionList<E> {
   public IrisObjectSelectionList(Minecraft client, int width, int height, int top, int bottom, int left, int right, int itemHeight) {
      super(client, width, height, top, itemHeight);
   }

   protected int scrollBarX() {
      return this.width - 6;
   }

   public void select(int entry) {
      this.setSelected(this.children().get(entry));
   }

   public void updateWidgetNarration(NarrationElementOutput p0) {
   }
}
