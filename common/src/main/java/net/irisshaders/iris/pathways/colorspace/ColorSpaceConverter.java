package net.irisshaders.iris.pathways.colorspace;

import com.mojang.renderpearl.backend.opengl.GlTexture;

public interface ColorSpaceConverter {
   void rebuildProgram(int var1, int var2, ColorSpace var3);

   void process(GlTexture var1);
}
