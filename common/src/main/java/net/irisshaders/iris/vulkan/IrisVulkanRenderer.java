package net.irisshaders.iris.vulkan;

import com.google.common.base.Throwables;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import java.util.Locale;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.sodium.mixin.SodiumChunkSectionAccessor;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.BlockMaterialMapping;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.properties.CloudSetting;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.vertices.sodium.terrain.FormatAnalyzer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
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
 *
 * <p>When the pack has terrain programs, Sodium meshes chunks in Iris's extended vertex format with the pack's block
 * IDs ({@link WorldRenderingSettings}, as IrisRenderingPipeline sets them on OpenGL), and the opaque terrain is drawn
 * with the pack's programs from the LevelRenderer hook just before vanilla's main pass.</p>
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

	/** Iris's Sodium vertex format with every extra attribute, as IrisRenderingPipeline uses on OpenGL. */
	private static final ChunkVertexType TERRAIN_VERTEX_TYPE = FormatAnalyzer.createFormat(true, true, true, true);

	private static IrisVulkanScreenPasses passes;
	/** The directives of the pack {@link #passes} were built for; null whenever passes is. */
	private static PackDirectives directives;
	private static ShaderPack syncedPack;
	/** The pack whose block IDs are in WorldRenderingSettings; they need a loaded world (block tags). */
	private static ShaderPack blockIdsFor;
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
		applyBlockIds();
		// Only between frames: a failure or pack switch can change the settings in the middle of the level render.
		reloadChunksIfRequired();
		logStateChange();
	}

	/**
	 * LevelRenderer's main pass, before vanilla opens it: starts the frame for the pack's targets and draws the opaque
	 * terrain with the pack's programs. {@link #drewOpaqueTerrain} then tells the main pass to skip it.
	 */
	public static void beforeMainPass(RenderTarget main, ChunkSectionsToRender sections) {
		if (!IrisVulkanBackend.EXPERIMENTAL_RENDERING || !IrisVulkanBackend.isActive() || passes == null) {
			return;
		}

		try {
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			passes.beginFrame(encoder, main);
			// Chunks meshed before the switch to the extended format (or the block IDs) are rebuilt first.
			if (passes.hasTerrain() && sections instanceof SodiumChunkSectionAccessor sodium
				&& WorldRenderingSettings.INSTANCE.getVertexFormat() == TERRAIN_VERTEX_TYPE && !WorldRenderingSettings.INSTANCE.isReloadRequired()) {
				passes.renderTerrain(encoder, main, sodium);
			}
		} catch (Throwable t) {
			fail(syncedPack, "terrain", t);
		}
	}

	/** Whether this frame's opaque terrain was already drawn with the pack's programs. */
	public static boolean drewOpaqueTerrain() {
		return passes != null && passes.terrainDrawnThisFrame();
	}

	/** Whether this frame's translucent terrain is drawn with the pack's program after deferred, so vanilla skips it. */
	public static boolean defersTranslucentTerrain() {
		return passes != null && passes.translucentTerrainPending();
	}

	/** The pack's {@code clouds=} directive while its passes run on Vulkan, else DEFAULT (vanilla's setting). */
	public static CloudSetting cloudSetting() {
		PackDirectives active = directives;
		return active != null ? active.getCloudSetting() : CloudSetting.DEFAULT;
	}

	/** Vanilla's vignette: off while a pack runs on Vulkan unless it sets {@code vignette=true}, as on OpenGL. */
	public static boolean shouldRenderVignette() {
		PackDirectives active = directives;
		return active == null || active.vignette();
	}

	/** Vanilla's underwater overlay: off while a pack runs unless it sets {@code underwaterOverlay=true}, as on OpenGL. */
	public static boolean shouldRenderUnderwaterOverlay() {
		PackDirectives active = directives;
		return active == null || active.underwaterOverlay();
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
			long frame = framesRendered + 1;
			boolean check = IrisVulkanPixelCheck.wants(frame);
			if (check) {
				IrisVulkanPixelCheck.beforePasses(encoder, main, frame);
			}
			passes.render(encoder, main);
			framesRendered = frame;
			if (frame == 1) {
				Iris.logger.info("[Iris/Vulkan] First world frame rendered through {}: executed {} into the {}x{} main target.",
					Iris.getCurrentPackName(), passes.executionOrder(), main.width, main.height);
			}
			if (check) {
				IrisVulkanPixelCheck.afterPasses(encoder, main, Iris.getCurrentPackName());
			}
		} catch (Throwable t) {
			IrisVulkanPixelCheck.abandon();
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
			applyWorldSettings(null);
			return;
		}

		if (pack == failedFor) {
			applyWorldSettings(null);
			return;
		}

		String name = Iris.getCurrentPackName();
		Iris.logger.info("[Iris/Vulkan] Building Vulkan screen passes for shader pack {} (dimension {}, {}x{}).", name, dimension, main.width, main.height);
		framesRendered = 0;
		IrisVulkanPixelCheck.reset();
		try {
			String key = "pack" + Integer.toHexString(System.identityHashCode(pack));
			ProgramSet programSet = pack.getProgramSet(dimension);
			passes = IrisVulkanScreenPasses.create(key, programSet, main.width, main.height, TERRAIN_VERTEX_TYPE.getVertexFormat());
			directives = programSet.getPackDirectives();
			failure = null;
			failedFor = null;
			applyWorldSettings(passes.hasTerrain() ? programSet.getPackDirectives() : null);
		} catch (Throwable t) {
			fail(pack, "build", t);
		}
	}

	/**
	 * What Sodium's meshing reads: the extended vertex format and the pack's lighting settings while terrain programs
	 * run ({@code directives} non-null), vanilla's otherwise (as VanillaRenderingPipeline). Changes rebuild the chunks
	 * on the next client tick.
	 */
	private static void applyWorldSettings(PackDirectives directives) {
		WorldRenderingSettings settings = WorldRenderingSettings.INSTANCE;
		if (directives != null) {
			settings.setVertexFormat(TERRAIN_VERTEX_TYPE);
			settings.setAmbientOcclusionLevel(directives.getAmbientOcclusionLevel());
			settings.setDisableDirectionalShading(!directives.isOldLighting());
			settings.setUseSeparateAo(directives.shouldUseSeparateAo());
			settings.setVoxelizeLightBlocks(directives.shouldVoxelizeLightBlocks());
		} else {
			settings.setVertexFormat(ChunkMeshFormats.COMPACT);
			settings.setAmbientOcclusionLevel(1.0F);
			settings.setDisableDirectionalShading(false);
			settings.setUseSeparateAo(false);
			settings.setVoxelizeLightBlocks(false);
			settings.setBlockTypeIds(Object2ObjectMaps.emptyMap());
			blockIdsFor = null;
		}
	}

	/** The pack's block IDs and render-layer overrides, once a world (and with it block tags) exists. */
	private static void applyBlockIds() {
		ShaderPack pack = syncedPack;
		if (passes == null || !passes.hasTerrain() || pack == null || pack == blockIdsFor || Minecraft.getInstance().level == null) {
			return;
		}

		blockIdsFor = pack;
		WorldRenderingSettings.INSTANCE.setBlockStateIds(BlockMaterialMapping.createBlockStateIdMap(pack.getIdMap().getBlockProperties(), pack.getIdMap().getTagEntries()));
		WorldRenderingSettings.INSTANCE.setBlockTypeIds(BlockMaterialMapping.createBlockTypeMap(pack.getIdMap().getBlockRenderTypeMap()));
	}

	private static void reloadChunksIfRequired() {
		if (WorldRenderingSettings.INSTANCE.isReloadRequired()) {
			if (Minecraft.getInstance().levelExtractor != null) {
				Iris.logger.info("[Iris/Vulkan] Terrain vertex format or block IDs changed; rebuilding chunks.");
				Minecraft.getInstance().levelExtractor.allChanged();
			}
			WorldRenderingSettings.INSTANCE.clearReloadRequired();
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
		applyWorldSettings(null);
	}

	private static void release(String reason) {
		Iris.logger.info("[Iris/Vulkan] Releasing Vulkan screen passes ({}).", reason);
		passes.close();
		passes = null;
		directives = null;
	}
}
