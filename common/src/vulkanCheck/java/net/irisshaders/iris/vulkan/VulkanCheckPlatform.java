package net.irisshaders.iris.vulkan;

import java.nio.file.Path;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Stands in for the Fabric platform helpers when Iris's pack loader runs outside the game (vulkanShaderCheck). */
public final class VulkanCheckPlatform implements IrisPlatformHelpers {
	@Override
	public boolean isModLoaded(String modId) {
		return false;
	}

	@Override
	public String getVersion() {
		return "1.11.4";
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return false;
	}

	@Override
	public Path getGameDir() {
		return Path.of(System.getProperty("iris.vulkanCheck.dir", "."));
	}

	@Override
	public Path getConfigDir() {
		return getGameDir().resolve("config");
	}

	@Override
	public int compareVersions(String a, String b) {
		return a.compareTo(b);
	}

	@Override
	public KeyMapping registerKeyBinding(KeyMapping keyMapping) {
		return keyMapping;
	}

	@Override
	public boolean useELS() {
		return false;
	}

	@Override
	public BlockState getBlockAppearance(BlockAndTintGetter level, BlockState state, Direction direction, BlockPos pos) {
		return state;
	}
}
