package net.irisshaders.iris.vulkan;

import com.google.common.base.Throwables;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import java.util.Locale;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Drives the experimental Vulkan screen-pass renderer from the VKOnly GameRenderer hooks. Builds
 * {@link IrisVulkanScreenPasses} for the current pack and dimension, rebuilds them after a reload or dimension
 * change, and records failures as a diagnostic instead of crashing or silently falling back to OpenGL.
 */
public final class IrisVulkanRenderer {
	public enum State {
		/** {@code -Diris.vulkan.experimental=true} not set: nothing renders through Iris on Vulkan. */
		DISABLED,
		/** No shader pack is loaded (shaders off or no pack selected). */
		NO_PACK,
		RENDERING,
		FAILED
	}

	private static IrisVulkanScreenPasses passes;
	private static ShaderPack builtFor;
	private static NamespacedId builtDimension;
	private static ShaderPack failedFor;
	private static String failure;
	private static long framesRendered;

	private IrisVulkanRenderer() {
	}

	public static State state() {
		if (!IrisVulkanBackend.EXPERIMENTAL_RENDERING) {
			return State.DISABLED;
		}

		if (failure != null && Iris.getCurrentPack().orElse(null) == failedFor) {
			return State.FAILED;
		}

		return passes == null ? State.NO_PACK : State.RENDERING;
	}

	public static String describe() {
		return switch (state()) {
			case DISABLED -> "Experimental Vulkan screen passes are off (launch with -Diris.vulkan.experimental=true to test them).";
			case NO_PACK -> "Experimental renderer enabled; no shader pack loaded.";
			case RENDERING -> String.format(Locale.ROOT, "Rendering %s screen passes (%d frames). %s",
				Iris.getCurrentPackName(), framesRendered, IrisVulkanPixelCheck.describe());
			case FAILED -> "Failed for " + Iris.getCurrentPackName() + ": " + failure;
		};
	}

	/** GameRenderer#renderLevel, just before render3dHud clears the depth buffer for the hand. */
	public static void beforeHud(RenderTarget main) {
		if (passes != null) {
			try {
				passes.captureWorldDepth(RenderSystem.getDevice().createCommandEncoder(), main);
			} catch (Throwable t) {
				fail(builtFor, t);
			}
		}
	}

	/** GameRenderer#render, right after renderLevel: run the pack's screen passes over the rendered frame. */
	public static void afterLevel(RenderTarget main) {
		if (!IrisVulkanBackend.EXPERIMENTAL_RENDERING || !IrisVulkanBackend.isActive()) {
			return;
		}

		ShaderPack pack = Iris.getCurrentPack().orElse(null);
		NamespacedId dimension = Iris.getCurrentDimension();
		if (pack != builtFor || (dimension != null && !dimension.equals(builtDimension))) {
			destroy();
			if (pack != null && pack != failedFor) {
				build(pack, dimension, main);
			}
		}

		if (passes == null) {
			return;
		}

		try {
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			passes.render(encoder, main);
			framesRendered++;
			IrisVulkanPixelCheck.afterFrame(encoder, main, Iris.getCurrentPackName(), framesRendered);
		} catch (Throwable t) {
			fail(pack, t);
		}
	}

	private static void build(ShaderPack pack, NamespacedId dimension, RenderTarget main) {
		builtFor = pack;
		builtDimension = dimension;
		framesRendered = 0;
		IrisVulkanPixelCheck.reset();
		try {
			String key = "pack" + Integer.toHexString(System.identityHashCode(pack));
			passes = new IrisVulkanScreenPasses(key, pack.getProgramSet(dimension), main.width, main.height);
			failure = null;
			failedFor = null;
		} catch (Throwable t) {
			fail(pack, t);
		}
	}

	private static void fail(ShaderPack pack, Throwable t) {
		failedFor = pack;
		failure = Throwables.getRootCause(t).toString();
		Iris.logger.error("[Iris/Vulkan] Screen passes failed for " + Iris.getCurrentPackName() + "; the frame is left as vanilla rendered it.", t);
		if (Minecraft.getInstance().player != null) {
			Minecraft.getInstance().player.sendSystemMessage(
				Component.literal("[Iris/Vulkan] " + Iris.getCurrentPackName() + " failed: " + failure).withStyle(ChatFormatting.RED)
			);
		}
		destroy();
		builtFor = pack;
	}

	private static void destroy() {
		if (passes != null) {
			passes.close();
			passes = null;
		}
		builtFor = null;
		builtDimension = null;
	}
}
