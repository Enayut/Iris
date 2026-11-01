package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.irisshaders.iris.Iris;

/**
 * In-game proof that the screen passes produced the right pixels across the whole screen
 * ({@code -Diris.vulkan.pixelCheck=true}).
 *
 * <p>On a check frame, a 5x5 grid of the main target (corners, edge midpoints, quarter points, centre; see
 * {@link IrisVulkanPixelModel#samplePositions}) is copied to a mappable buffer twice in the same command stream: once
 * before the screen passes (the scene vanilla rendered) and once after the final pass. Once the GPU has finished,
 * every result sample is compared with {@link IrisVulkanPixelModel}'s prediction for the bundled test packs. Other
 * packs only get the raw values logged.</p>
 */
final class IrisVulkanPixelCheck {
	private static final boolean ENABLED = Boolean.getBoolean("iris.vulkan.pixelCheck");
	private static final long FIRST_CHECK_FRAME = 60;
	private static final long CHECK_INTERVAL = 600;
	private static final int MAX_LOGGED_MISMATCHES = 6;

	private static volatile String lastResult = "pixel check off (-Diris.vulkan.pixelCheck=true)";
	private static boolean pending;
	private static Capture capture;

	private record Capture(int[][] positions, GpuBuffer scene, GpuBuffer result, int width, int height, long frame) {
	}

	private IrisVulkanPixelCheck() {
	}

	static String describe() {
		return lastResult;
	}

	/** True once a check has read back the expected values at every sample. */
	static boolean passed() {
		return lastResult.startsWith("pixel check PASS");
	}

	static void reset() {
		lastResult = ENABLED ? "pixel check pending" : "pixel check off (-Diris.vulkan.pixelCheck=true)";
	}

	/** Whether {@code frame} (1-based, the frame about to be rendered) is a check frame. */
	static boolean wants(long frame) {
		return ENABLED && !pending && (frame == FIRST_CHECK_FRAME || (frame > FIRST_CHECK_FRAME && (frame - FIRST_CHECK_FRAME) % CHECK_INTERVAL == 0));
	}

	/** Before the screen passes: sample the scene at the grid. */
	static void beforePasses(CommandEncoder encoder, RenderTarget main, long frame) {
		int[][] positions = IrisVulkanPixelModel.samplePositions(main.width, main.height);
		capture = new Capture(positions, buffer("scene", positions.length), buffer("result", positions.length), main.width, main.height, frame);
		pending = true;
		copy(encoder, main, capture.scene, positions, () -> {
		});
	}

	/** After the final pass: sample the result and evaluate once the GPU is done with both copies. */
	static void afterPasses(CommandEncoder encoder, RenderTarget main, String packName) {
		Capture current = capture;
		capture = null;
		if (current == null) {
			return;
		}

		if (main.width != current.width || main.height != current.height) {
			release(current);
			pending = false;
			return;
		}

		copy(encoder, main, current.result, current.positions, () -> evaluate(current, packName));
	}

	/** The frame failed between the two copies: drop the capture without evaluating. */
	static void abandon() {
		if (capture != null) {
			Capture current = capture;
			capture = null;
			release(current);
		}
		pending = false;
	}

	private static GpuBuffer buffer(String label, int samples) {
		return RenderSystem.getDevice().createBuffer(() -> "iris vulkan pixel check " + label, GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, 4L * samples);
	}

	/** One 1x1 copy per sample; {@code done} runs after the last one has completed on the GPU. */
	private static void copy(CommandEncoder encoder, RenderTarget main, GpuBuffer destination, int[][] positions, Runnable done) {
		int[] remaining = {positions.length};
		for (int i = 0; i < positions.length; i++) {
			encoder.copyTextureToBuffer(main.getColorTexture(), destination, 4L * i, () -> {
				if (--remaining[0] == 0) {
					done.run();
				}
			}, 0, positions[i][0], positions[i][1], 1, 1);
		}
	}

	private static void evaluate(Capture capture, String packName) {
		try (GpuBufferSlice.MappedView sceneView = capture.scene.map(true, false); GpuBufferSlice.MappedView resultView = capture.result.map(true, false)) {
			IrisVulkanPixelModel.Model model = IrisVulkanPixelModel.forPack(packName);
			List<String> mismatches = new ArrayList<>();
			StringBuilder values = new StringBuilder();
			int matching = 0;
			for (int i = 0; i < capture.positions.length; i++) {
				int x = capture.positions[i][0];
				int y = capture.positions[i][1];
				int[] scene = rgb(sceneView.data(), i);
				int[] actual = rgb(resultView.data(), i);
				if (model == null) {
					values.append(String.format(Locale.ROOT, " (%d,%d)=%s", x, y, format(actual)));
					continue;
				}

				int[] expected = model.expected(x, y, capture.width, capture.height, scene);
				if (IrisVulkanPixelModel.matches(expected, actual)) {
					matching++;
				} else {
					mismatches.add(String.format(Locale.ROOT, "(%d,%d) got %s expected %s (scene %s)", x, y, format(actual), format(expected), format(scene)));
				}
			}

			String verdict = model == null ? "VALUES" : mismatches.isEmpty() ? "PASS" : "FAIL";
			lastResult = String.format(Locale.ROOT, "pixel check %s at frame %d (%dx%d, %d/%d samples)", verdict, capture.frame, capture.width, capture.height,
				model == null ? 0 : matching, capture.positions.length);
			if (model == null) {
				Iris.logger.info("[Iris/Vulkan] Pixel check VALUES for {} at frame {} ({}x{}), no model for this pack:{}", packName, capture.frame, capture.width, capture.height, values);
			} else {
				Iris.logger.info("[Iris/Vulkan] Pixel check {} for {} at frame {} ({}x{}): {}/{} samples match (5x5 grid: corners, edges, centre){}",
					verdict, packName, capture.frame, capture.width, capture.height, matching, capture.positions.length,
					mismatches.isEmpty() ? "" : "; first mismatches: " + String.join("; ", mismatches.subList(0, Math.min(MAX_LOGGED_MISMATCHES, mismatches.size()))));
			}
		} catch (Throwable t) {
			lastResult = "pixel check error: " + t;
			Iris.logger.error("[Iris/Vulkan] Pixel check readback failed", t);
		} finally {
			release(capture);
			pending = false;
		}
	}

	private static int[] rgb(ByteBuffer data, int sample) {
		return new int[]{data.get(sample * 4) & 0xFF, data.get(sample * 4 + 1) & 0xFF, data.get(sample * 4 + 2) & 0xFF};
	}

	private static String format(int[] rgb) {
		return "(" + rgb[0] + "," + rgb[1] + "," + rgb[2] + ")";
	}

	private static void release(Capture capture) {
		capture.scene.close();
		capture.result.close();
	}
}
