package net.irisshaders.iris.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public interface IrisPlatformHelpers {
   IrisPlatformHelpers INSTANCE = ServiceLoader.load(IrisPlatformHelpers.class).findFirst().get();

   static IrisPlatformHelpers getInstance() {
      return INSTANCE;
   }

   boolean isModLoaded(String var1);

   String getVersion();

   boolean isDevelopmentEnvironment();

   Path getGameDir();

   Path getConfigDir();

   int compareVersions(String var1, String var2) throws Exception;

   KeyMapping registerKeyBinding(KeyMapping var1);

   boolean useELS();

   BlockState getBlockAppearance(BlockAndTintGetter var1, BlockState var2, Direction var3, BlockPos var4);
}
