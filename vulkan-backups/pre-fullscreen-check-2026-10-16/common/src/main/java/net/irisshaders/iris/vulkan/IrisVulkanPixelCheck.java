package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import java.nio.ByteBuffer;
import java.util.Locale;
import net.irisshaders.iris.Iris;

/**
 * In-game proof that the screen passes produced real pixels ({@code -Diris.vulkan.pixelCheck=true}). After the
 * final pass, three marker pixels of the main target are copied to a mappable buffer through RenderPearl and read
 * back once the GPU has finished the frame.
 *
 * <p>For the bundled {@code IrisVulkanSmoke} test pack (testpacks/IrisVulkanSmoke) the markers have known values:
 * <ul>
 *   <li>A, bottom-left: constant (0.25, 0.5, 0.75) written by final.fsh - the final pass ran and reached the screen.</li>
 *   <li>B, bottom-right: (0.5, 0.25, 1.0) written by composite.fsh into colortex1 and copied by final.fsh - pass
 *       ordering, render target writes and buffer flipping work.</li>
 *   <li>C, top-right: (viewWidth/4096, viewHeight/4096, 1.0) - the std140 uniform block carries Iris's values.</li>
 * </ul>
 * Any other pack only gets the raw values logged.</p>
 */
final class IrisVulkanPixelCheck {
	private static final boolean ENABLED = Boolean.getBoolean("iris.vulkan.pixelCheck");
	private static final int TOLERANCE = 2;
	private static final long FIRST_CHECK_FRAME = 60;
	private static final long CHECK_INTERVAL = 600;

	private static volatile String lastResult = "pixel check off (-Diris.vulkan.pixelCheck=true)";
	private static boolean pending;

	private IrisVulkanPixelCheck() {
	}

	static String describe() {
		return lastResult;
	}

	/** True once the smoke-pack markers have been read back with the expected values. */
	static boolean passed() {
		return lastResult.startsWith("pixel check PASS");
	}

	static void reset() {
		lastResult = ENABLED ? "pixel check pending" : "pixel check off (-Diris.vulkan.pixelCheck=true)";
	}

	static void afterFrame(CommandEncoder encoder, RenderTarget main, String packName, long frame) {
		if (!ENABLED || pending || (frame != FIRST_CHECK_FRAME && (frame < FIRST_CHECK_FRAME || (frame - FIRST_CHECK_FRAME) % CHECK_INTERVAL != 0))) {
			return;
		}

		int width = main.width;
		int height = main.height;
		int[][] positions = {{8, 8}, {width - 8, 8}, {width - 8, height - 8}};
		int[][] expected = packName.contains("IrisVulkanSmoke") ? new int[][]{
			{64, 128, 191},
			{128, 64, 255},
			{Math.round(width / 4096.0f * 255.0f), Math.round(height / 4096.0f * 255.0f), 255}
		} : null;

		GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "iris vulkan pixel check", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, 4L * positions.length);
		pending = true;
		int[] remaining = {positions.length};
		for (int i = 0; i < positions.length; i++) {
			encoder.copyTextureToBuffer(main.getColorTexture(), buffer, 4L * i, () -> {
				if (--remaining[0] == 0) {
					evaluate(buffer, positions, expected, packName, width, height, frame);
				}
			}, 0, positions[i][0], positions[i][1], 1, 1);
		}
	}

	private static void evaluate(GpuBuffer buffer, int[][] positions, int[][] expected, String packName, int width, int height, long frame) {
		try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
			ByteBuffer data = view.data();
			StringBuilder values = new StringBuilder();
			boolean pass = true;
			for (int i = 0; i < positions.length; i++) {
				int r = data.get(i * 4) & 0xFF;
				int g = data.get(i * 4 + 1) & 0xFF;
				int b = data.get(i * 4 + 2) & 0xFF;
				values.append(String.format(Locale.ROOT, " %c(%d,%d)=(%d,%d,%d)", (char) ('A' + i), positions[i][0], positions[i][1], r, g, b));
				if (expected != null) {
					values.append(String.format(Locale.ROOT, " expected (%d,%d,%d);", expected[i][0], expected[i][1], expected[i][2]));
					pass &= Math.abs(r - expected[i][0]) <= TOLERANCE && Math.abs(g - expected[i][1]) <= TOLERANCE && Math.abs(b - expected[i][2]) <= TOLERANCE;
				}
			}

			String verdict = expected == null ? "VALUES" : pass ? "PASS" : "FAIL";
			lastResult = String.format(Locale.ROOT, "pixel check %s at frame %d (%dx%d)", verdict, frame, width, height);
			Iris.logger.info("[Iris/Vulkan] Pixel check {} for {} at frame {} ({}x{}):{}", verdict, packName, frame, width, height, values);
		} catch (Throwable t) {
			lastResult = "pixel check error: " + t;
			Iris.logger.error("[Iris/Vulkan] Pixel check readback failed", t);
		} finally {
			buffer.close();
			pending = false;
		}
	}
}
