package net.irisshaders.iris.uniforms;

import java.util.ArrayList;
import java.util.List;

public class FrameUpdateNotifier {
   private final List<Runnable> listeners = new ArrayList<>();

   public void addListener(Runnable onNewFrame) {
      this.listeners.add(onNewFrame);
   }

   public void onNewFrame() {
      this.listeners.forEach(Runnable::run);
   }
}
