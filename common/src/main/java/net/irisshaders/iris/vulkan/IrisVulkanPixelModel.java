package net.irisshaders.iris.vulkan;

/**
 * CPU reference models of the bundled test packs' final output, used by {@link IrisVulkanPixelCheck}. Each model
 * re-implements its pack's shaders for one pixel, given the scene colour at that pixel before the passes ran.
 * Coordinates are memory rows/columns of the main target; row 0 is where {@code gl_FragCoord.y} is 0.5, as on
 * OpenGL (RenderPearl's Vulkan viewport is not flipped).
 */
final class IrisVulkanPixelModel {
	/** Allowed difference per 8-bit channel: each RGBA8 store can round by one step. */
	static final int TOLERANCE = 2;

	private IrisVulkanPixelModel() {
	}

	/** The pack whose output is predictable, or null when only raw values can be logged. */
	static Model forPack(String packName) {
		if (packName.contains("IrisVulkanFullscreen")) {
			return IrisVulkanPixelModel::fullscreen;
		}
		if (packName.contains("IrisVulkanSmoke")) {
			return IrisVulkanPixelModel::smoke;
		}
		return null;
	}

	interface Model {
		/** @return expected RGB bytes at (x, y), given the scene's RGB bytes there before the passes */
		int[] expected(int x, int y, int width, int height, int[] scene);
	}

	/**
	 * 5x5 sample grid: both edges, the quarter points and the centre on each axis, so corners, edge midpoints and
	 * the screen centre are always included. Duplicates collapse on very small targets.
	 */
	static int[][] samplePositions(int width, int height) {
		int[] xs = axis(width);
		int[] ys = axis(height);
		java.util.LinkedHashSet<Long> unique = new java.util.LinkedHashSet<>();
		for (int y : ys) {
			for (int x : xs) {
				unique.add(((long) x << 32) | y);
			}
		}
		return unique.stream().map(packed -> new int[]{(int) (packed >>> 32), (int) (long) packed}).toArray(int[][]::new);
	}

	private static int[] axis(int size) {
		return new int[]{0, size / 4, size / 2, size * 3 / 4, size - 1};
	}

	/** testpacks/IrisVulkanSmoke: grayscale left half, scene right half, markers A/B/C (see final.fsh). */
	static int[] smoke(int x, int y, int width, int height, int[] scene) {
		float fragX = x + 0.5f;
		float fragY = y + 0.5f;
		int gray = toByte(luminance(scene));
		// final.fsh applies these in order; later assignments win.
		int[] color = fragX / width < 0.5f ? new int[]{gray, gray, gray} : scene.clone();
		if (fragX > width - 32.0f && fragY < 32.0f) {
			color = new int[]{toByte(0.5f), toByte(0.25f), toByte(1.0f)};
		}
		if (fragX < 32.0f && fragY < 32.0f) {
			color = new int[]{toByte(0.25f), toByte(0.5f), toByte(0.75f)};
		}
		if (fragX > width - 32.0f && fragY > height - 32.0f) {
			color = new int[]{toByte(width / 4096.0f), toByte(height / 4096.0f), 255};
		}
		return color;
	}

	/** testpacks/IrisVulkanFullscreen: red/green = the composite's texture coordinate, blue = scene luminance. */
	static int[] fullscreen(int x, int y, int width, int height, int[] scene) {
		return new int[]{toByte((x + 0.5f) / width), toByte((y + 0.5f) / height), toByte(luminance(scene))};
	}

	private static float luminance(int[] rgb) {
		return (0.299f * rgb[0] + 0.587f * rgb[1] + 0.114f * rgb[2]) / 255.0f;
	}

	private static int toByte(float value) {
		return Math.round(Math.max(0.0f, Math.min(1.0f, value)) * 255.0f);
	}

	static boolean matches(int[] expected, int[] actual) {
		for (int c = 0; c < 3; c++) {
			if (Math.abs(expected[c] - actual[c]) > TOLERANCE) {
				return false;
			}
		}
		return true;
	}
}
