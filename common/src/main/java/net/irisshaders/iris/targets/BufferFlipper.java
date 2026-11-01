package net.irisshaders.iris.targets;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class BufferFlipper {
   private final IntSet flippedBuffers = new IntOpenHashSet();

   public void flip(int target) {
      if (!this.flippedBuffers.remove(target)) {
         this.flippedBuffers.add(target);
      }
   }

   public boolean isFlipped(int target) {
      return this.flippedBuffers.contains(target);
   }

   public IntIterator getFlippedBuffers() {
      return this.flippedBuffers.iterator();
   }

   public ImmutableSet<Integer> snapshot() {
      return ImmutableSet.copyOf(this.flippedBuffers);
   }
}
