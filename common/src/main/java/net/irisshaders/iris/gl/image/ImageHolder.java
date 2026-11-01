package net.irisshaders.iris.gl.image;

import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;

public interface ImageHolder {
   boolean hasImage(String var1);

   void addTextureImage(IntSupplier var1, InternalTextureFormat var2, String var3);
}
