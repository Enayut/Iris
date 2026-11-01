package net.irisshaders.iris.pbr.loader;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

public interface PBRTextureLoader<T extends AbstractTexture> {
   void load(T var1, ResourceManager var2, PBRTextureLoader.PBRTextureConsumer var3);

   interface PBRTextureConsumer {
      void acceptNormalTexture(@NotNull AbstractTexture var1);

      void acceptSpecularTexture(@NotNull AbstractTexture var1);
   }
}
