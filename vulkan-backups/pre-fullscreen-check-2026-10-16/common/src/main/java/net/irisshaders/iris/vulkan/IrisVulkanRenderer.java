package net.irisshaders.iris.vulkan;

import com.google.common.base.Throwables;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import java.util.Locale;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Drives the experimental Vulkan screen-pass renderer. The shader pack itself is owned by Iris
 * ({@link Iris#getCurrentPack()}, set by {@link Iris#loadShaderpack()}); this class follows it.
 *
 * <p>Every client tick and every rendered frame it syncs with Iris: when the pack object or the dimension changes,
 * the old passes are released and new ones are built (transformed, translated, compiled and registered) right
 * away, so a broken pack is reported at the title screen rather than on the first world frame. The passes execute
 * from the GameRenderer hook after the level is rendered. Failures are recorded as a diagnostic; nothing falls
 * back to OpenGL.</p>
 */
public final class IrisVulkanRenderer {
	public enum State {
		/** {@code -Diris.vulkan.experimental=true} not set: nothing renders through Iris on Vulkan. */
		DISABLED,
		/** Iris has no shader pack (shaders off, or no pack selected in iris.properties). */
		NO_PACK,
		/** Iris has a pack, but the renderer has not synced with it yet. */
		PENDING,
		/** Passes are compiled and registered; no world frame has run them yet. */
		PREPARED,
		/** Passes have executed on at least one world frame. */
		RENDERING,
		FAILED
	}

	private static IrisVulkanScreenPasses passes;
	private static ShaderPack syncedPack;
	private static NamespacedId syncedDimension;
	private static ShaderPack failedFor;
	private static String failure;
	private static long framesRendered;
	private static State loggedState;

	private IrisVulkanRenderer() {
	}

	public static State state() {
		if (!IrisVulkanBackend.EXPERIMENTAL_RENDERING) {
			return State.DISABLED;
		}

		ShaderPack pack = Iris.getCurrentPack().orElse(null);
		if (pack == null) {
			return State.NO_PACK;
		}

		if (pack == failedFor) {
			return State.FAILED;
		}

		if (passes == null || pack != syncedPack) {
			return State.PENDING;
		}

		return framesRendered == 0 ? State.PREPARED : State.RENDERING;
	}

	public static String describe() {
		String pack = Iris.getCurrentPackName();
		return switch (state()) {
			case DISABLED -> "Experimental Vulkan screen passes are off (launch with -Diris.vulkan.experimental=true to test them).";
			case NO_PACK -> "Iris has no shader pack loaded (iris.properties: enableShaders=" + Iris.getIrisConfig().areShadersEnabled()
				+ ", shaderPack=" + Iris.getIrisConfig().getShaderPackName().orElse("<none>") + ").";
			case PENDING -> "Iris loaded " + pack + "; Vulkan screen passes not built yet.";
			case PREPARED -> pack + ": " + passes.passNames() + " compiled for Vulkan (dimension " + syncedDimension + "); waiting for a world frame.";
			case RENDERING -> String.format(Locale.ROOT, "Rendering %s: %s, %d frames, %s.", pack, passes.passNames(), framesRendered, IrisVulkanPixelCheck.describe());
			case FAILED -> pack + " failed: " + failure;
		};
	}

	/** Client tick on Vulkan (IrisVKOnly): follow Iris's pack so it is built as soon as Iris loads it. */
	public static void tick() {
		if (!IrisVulkanBackend.EXPERIMENTAL_RENDERING || !IrisVulkanBackend.isActive()) {
			return;
		}

		sync(Minecraft.getInstance().gameRenderer.mainRenderTarget());
		logStateChange();
	}

	/** GameRenderer#renderLevel, just before render3dHud clears the depth buffer for the hand. */
	public static void beforeHud(RenderTarget main) {
		if (passes != null) {
			try {
				passes.captureWorldDepth(RenderSystem.getDevice().createCommandEncoder(), main);
			} catch (Throwable t) {
				fail(syncedPack, "world depth capture", t);
			}
		}
	}

	/** GameRenderer#render, right after renderLevel: run the pack's screen passes over the rendered frame. */
	public static void afterLevel(RenderTarget main) {
		if (!IrisVulkanBackend.EXPERIMENTAL_RENDERING || !IrisVulkanBackend.isActive()) {
			return;
		}

		sync(main);
		if (passes == null) {
			return;
		}

		try {
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			passes.render(encoder, main);
			if (framesRendered++ == 0) {
				Iris.logger.info("[Iris/Vulkan] First world frame rendered through {}: executed {} into the {}x{} main target.",
					Iris.getCurrentPackName(), passes.executionOrder(), main.width, main.height);
			}
			IrisVulkanPixelCheck.afterFrame(encoder, main, Iris.getCurrentPackName(), framesRendered);
		} catch (Throwable t) {
			fail(syncedPack, "frame execution", t);
		}

		logStateChange();
	}

	private static void sync(RenderTarget main) {
		ShaderPack pack = Iris.getCurrentPack().orElse(null);
		// Before a world exists Iris has no dimension; packs are built for the overworld, which Iris also assumes.
		NamespacedId dimension = Iris.getCurrentDimension();
		if (dimension == null) {
			dimension = DimensionId.OVERWORLD;
		}

		if (pack == syncedPack && dimension.equals(syncedDimension)) {
			return;
		}

		if (passes != null) {
			release(pack != syncedPack ? "Iris switched shader pack" : "dimension changed to " + dimension);
		}

		syncedPack = pack;
		syncedDimension = dimension;
		if (pack == null) {
			Iris.logger.info("[Iris/Vulkan] Iris has no shader pack; Vulkan screen passes are idle.");
			return;
		}

		if (pack == failedFor) {
			return;
		}

		String name = Iris.getCurrentPackName();
		Iris.logger.info("[Iris/Vulkan] Building Vulkan screen passes for shader pack {} (dimension {}, {}x{}).", name, dimension, main.width, main.height);
		framesRendered = 0;
		IrisVulkanPixelCheck.reset();
		try {
			String key = "pack" + Integer.toHexString(System.identityHashCode(pack));
			passes = IrisVulkanScreenPasses.create(key, pack.getProgramSet(dimension), main.width, main.height);
			failure = null;
			failedFor = null;
		} catch (Throwable t) {
			fail(pack, "build", t);
		}
	}

	private static void logStateChange() {
		State state = state();
		if (state != loggedState) {
			loggedState = state;
			Iris.logger.info("[Iris/Vulkan] Screen passes {}: {}", state, describe());
		}
	}

	private static void fail(ShaderPack pack, String phase, Throwable t) {
		failedFor = pack;
		failure = phase + ": " + Throwables.getRootCause(t);
		Iris.logger.error("[Iris/Vulkan] Screen passes for " + Iris.getCurrentPackName() + " failed during " + phase + "; frames are left as vanilla rendered them.", t);
		if (Minecraft.getInstance().player != null) {
			Minecraft.getInstance().player.sendSystemMessage(
				Component.literal("[Iris/Vulkan] " + Iris.getCurrentPackName() + " failed (" + failure + ")").withStyle(ChatFormatting.RED)
			);
		}
		if (passes != null) {
			release("failure");
		}
	}

	private static void release(String reason) {
		Iris.logger.info("[Iris/Vulkan] Releasing Vulkan screen passes ({}).", reason);
		passes.close();
		passes = null;
	}
}
