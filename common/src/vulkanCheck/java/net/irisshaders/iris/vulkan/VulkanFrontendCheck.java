package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.TransientMemory;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.device.DeviceFeatures;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.DeviceLimits;
import com.mojang.renderpearl.api.device.DeviceType;
import com.mojang.renderpearl.api.device.HintsAndWorkarounds;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import com.mojang.renderpearl.backend.common.BaseGpuBuffer;
import com.mojang.renderpearl.backend.common.BaseGpuTexture;
import com.mojang.renderpearl.backend.common.BaseGpuTextureView;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import com.mojang.renderpearl.util.TextureViewAndSampler;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.lwjgl.system.MemoryUtil;

/**
 * Runs {@link IrisVulkanScreenPasses} - build, two frames, close - through RenderPearl's real frontend
 * ({@link FrontendGpuDevice}, FrontendCommandEncoder, FrontendRenderPass) on top of a recording backend that issues
 * no GPU work. Every usage, format and lifetime rule RenderPearl's frontend enforces is enforced here exactly as in
 * game, including the {@code verifyColorTexture} check that rejected the 1x1 placeholder texture.
 *
 * <p>On top of RenderPearl's own validation it checks what the frontend only checks in IDE builds or not at all:
 * sampled textures carry {@code USAGE_TEXTURE_BINDING}, uniform blocks carry {@code USAGE_UNIFORM}, no pass samples a
 * texture it renders to, the smoke pack's passes read and write the targets Iris's flip rules prescribe, and every
 * texture the passes created is closed by {@link IrisVulkanScreenPasses#close()}.</p>
 *
 * <p>It does not render anything: pixels are only verified in game by IrisVulkanPixelCheck.</p>
 */
final class VulkanFrontendCheck {
	/** Stand-ins for the RTX 3050's RenderPearl device; the values the frontend validates against. */
	private static final DeviceInfo DEVICE_INFO = new DeviceInfo(
		"NVIDIA GeForce RTX 3050 Laptop GPU", "NVIDIA", "vulkanShaderCheck recording backend", true, "Vulkan", 1.0f,
		new DeviceLimits(16, 64, 32768, 4L << 30, 0, 8, 0),
		new DeviceFeatures(true, true, false, false, true, true, true, true),
		Set.of(), new HintsAndWorkarounds(false, false, false, false), DeviceType.DISCRETE
	);

	record PassRecord(String label, List<GpuTexture> attachments, RenderPass.RenderArea renderArea, List<GpuTexture> sampled, List<GpuBuffer> uniformBuffers, int draws) {
	}

	private final List<RecordingTexture> textures = new ArrayList<>();
	private final List<RecordingView> views = new ArrayList<>();
	private final List<PassRecord> passes = new ArrayList<>();
	private final List<String> clears = new ArrayList<>();
	private PassRecord currentPass;
	private final Consumer<String> failure;
	private int checks;

	private VulkanFrontendCheck(Consumer<String> failure) {
		this.failure = failure;
	}

	/** @return number of checks run; failures are reported through {@code failure} */
	static int run(Path smokePack, Path work, List<Path> terrainPacks, Consumer<String> failure) throws Exception {
		VulkanFrontendCheck check = new VulkanFrontendCheck(failure);
		check.installDevice();
		check.runPack(smokePack, "IrisVulkanSmoke", check::checkSmokeRouting);
		check.runPack(smokePack.resolveSibling("IrisVulkanFullscreen"), "IrisVulkanFullscreen", check::checkFullscreenRouting);
		for (Path pack : terrainPacks) {
			check.runTerrainPack(pack);
		}
		check.runFailingBuild(work);
		check.checkPixelModel();
		return check.checks;
	}

	/** A pack that cannot be translated (sampler3D) must fail to build and release everything the build created. */
	private void runFailingBuild(Path work) throws Exception {
		Path shaders = work.resolve("failing-build-pack/shaders");
		java.nio.file.Files.createDirectories(shaders);
		String vertex = "#version 120\nvarying vec2 texcoord;\nvoid main() { gl_Position = ftransform(); texcoord = gl_MultiTexCoord0.xy; }\n";
		java.nio.file.Files.writeString(shaders.resolve("composite.vsh"), vertex);
		java.nio.file.Files.writeString(shaders.resolve("composite.fsh"),
			"#version 120\nuniform sampler3D volume;\nvarying vec2 texcoord;\nvoid main() { gl_FragData[0] = texture3D(volume, vec3(texcoord, 0.5)); }\n");

		ShaderPack pack = new ShaderPack(shaders, Map.of(), StandardMacros.createStandardEnvironmentDefines(), false);
		int texturesBefore = textures.size();
		try {
			new IrisVulkanScreenPasses("failingbuild", pack.getProgramSet(DimensionId.OVERWORLD), new IrisVulkanUniforms(), new FrameUpdateNotifier(), 854, 480).close();
			expect("an untranslatable pack fails to build", false, "it built");
		} catch (IrisVulkanGlslTranslator.TranslationException e) {
			expect("an untranslatable pack fails to build", e.getMessage().contains("sampler3D"), e.getMessage());
		}
		List<String> leaked = textures.subList(texturesBefore, textures.size()).stream()
			.filter(texture -> !texture.isClosed()).map(GpuTexture::getLabel).collect(Collectors.toList());
		int created = textures.size() - texturesBefore;
		expect("the failing build had allocated textures (so their release is exercised)", created > 0, "created=" + created);
		expect("a failed build releases what it created", leaked.isEmpty(), "still open: " + leaked);
		System.out.println("  failing build (sampler3D)          " + (leaked.isEmpty() ? "OK  rejected, " + created + " textures created and all released" : "FAIL leaked " + leaked));
	}

	private FrontendGpuDevice device;

	private void installDevice() throws ReflectiveOperationException {
		device = new FrontendGpuDevice(backend());
		Field.set(RenderSystem.class, "DEVICE", device);
		if (!RenderSystem.isOnRenderThread()) {
			RenderSystem.initRenderThread();
		}
	}

	private void runPack(Path packDir, String packName, Consumer<MainTarget> routing) throws Exception {
		System.out.println("== RenderPearl frontend validation (" + packName + ")");
		ShaderPack pack = new ShaderPack(packDir.resolve("shaders"), Map.of(), StandardMacros.createStandardEnvironmentDefines(), false);
		MainTarget main = new MainTarget(854, 480);
		int texturesBeforeBuild = textures.size();
		int viewsBeforeBuild = views.size();

		FrameUpdateNotifier notifier = new FrameUpdateNotifier();
		IrisVulkanUniforms uniforms = new IrisVulkanUniforms();
		uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "viewWidth", () -> (float) main.width);
		uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "viewHeight", () -> (float) main.height);

		IrisVulkanScreenPasses screenPasses;
		try {
			screenPasses = new IrisVulkanScreenPasses(packName.toLowerCase(java.util.Locale.ROOT), pack.getProgramSet(DimensionId.OVERWORLD), uniforms, notifier, main.width, main.height);
			expect("build passes RenderPearl validation", true, "");
		} catch (RuntimeException e) {
			expect("build passes RenderPearl validation", false, e.toString());
			System.out.println("  build                              FAIL " + e);
			expectBuildFailureReleasesResources(texturesBeforeBuild, viewsBeforeBuild);
			return;
		}
		System.out.println("  build                              OK");

		CommandEncoder encoder = device.createCommandEncoder();
		for (int frame = 1; frame <= 2; frame++) {
			passes.clear();
			clears.clear();
			// As VKOnly_GameRendererHooks does at the start of every frame; per-frame work is keyed on this counter.
			net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.beginFrame();
			try {
				screenPasses.captureWorldDepth(encoder, main);
				screenPasses.render(encoder, main);
				expect("frame " + frame + " passes RenderPearl validation", true, "");
				System.out.println("  frame " + frame + "                            OK  " + passes.stream().map(PassRecord::label).toList());
			} catch (RuntimeException e) {
				expect("frame " + frame + " passes RenderPearl validation", false, e.toString());
				System.out.println("  frame " + frame + "                            FAIL " + e);
			}
		}

		checkUsage();
		checkCoverage(main);
		routing.accept(main);

		screenPasses.close();
		List<String> leaked = textures.subList(texturesBeforeBuild, textures.size()).stream()
			.filter(texture -> !texture.isClosed()).map(GpuTexture::getLabel).collect(Collectors.toList());
		List<String> leakedViews = views.subList(viewsBeforeBuild, views.size()).stream()
			.filter(view -> !view.isClosed()).map(view -> view.texture().getLabel()).collect(Collectors.toList());
		expect("close() releases every texture it created", leaked.isEmpty(), "still open: " + leaked);
		expect("close() releases every texture view it created", leakedViews.isEmpty(), "still open: " + leakedViews);
		System.out.println("  close                              " + (leaked.isEmpty() && leakedViews.isEmpty() ? "OK  all textures and views released" : "FAIL open: " + leaked + leakedViews));
	}

	/**
	 * A real pack with gbuffers_terrain: its terrain pipelines (Iris's extended Sodium vertex format, Sodium's
	 * resources, push constants, the pack's draw buffers) and screen passes through RenderPearl's PipelineBuilder,
	 * one frame without terrain, and the two merge passes a terrain frame adds. Drawing the terrain itself needs
	 * Sodium's world renderer, which only exists in game.
	 */
	private void runTerrainPack(Path packPath) throws Exception {
		String packName = packPath.getFileName().toString();
		System.out.println("== RenderPearl frontend validation, terrain (" + packName + ")");
		java.nio.file.FileSystem zip = java.nio.file.Files.isDirectory(packPath) ? null : java.nio.file.FileSystems.newFileSystem(packPath);
		try {
			Path shaders = zip == null ? packPath.resolve("shaders") : zip.getPath("shaders");
			ShaderPack pack = new ShaderPack(shaders, Map.of(), StandardMacros.createStandardEnvironmentDefines(), zip != null);
			MainTarget main = new MainTarget(854, 480);
			int texturesBeforeBuild = textures.size();
			int viewsBeforeBuild = views.size();

			IrisVulkanUniforms uniforms = new IrisVulkanUniforms();
			IrisVulkanTerrain.addUniforms(uniforms);
			com.mojang.renderpearl.api.vertex.VertexFormat format =
				net.irisshaders.iris.vertices.sodium.terrain.FormatAnalyzer.createFormat(true, true, true, true).getVertexFormat();

			IrisVulkanScreenPasses screenPasses;
			try {
				screenPasses = new IrisVulkanScreenPasses("terraincheck", pack.getProgramSet(DimensionId.OVERWORLD), uniforms, new FrameUpdateNotifier(), main.width, main.height, format);
			} catch (RuntimeException e) {
				expect(packName + ": build with terrain passes RenderPearl validation", false, e.toString());
				System.out.println("  build with terrain                 FAIL " + e);
				expectBuildFailureReleasesResources(texturesBeforeBuild, viewsBeforeBuild);
				return;
			}
			expect(packName + ": terrain programs were built", screenPasses.hasTerrain(), "");
			System.out.println("  build with terrain                 " + (screenPasses.hasTerrain() ? "OK  solid and cutout terrain pipelines accepted" : "FAIL no terrain programs"));
			System.out.println("  translucent terrain                " + (screenPasses.hasTranslucentTerrain() ? "OK  translucent terrain pipeline accepted" : "none (water stays vanilla)"));

			CommandEncoder encoder = device.createCommandEncoder();
			passes.clear();
			clears.clear();
			net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.beginFrame();
			try {
				screenPasses.captureWorldDepth(encoder, main);
				screenPasses.render(encoder, main);
				expect(packName + ": frame passes RenderPearl validation", true, "");
				System.out.println("  frame (no terrain drawn)           OK  " + passes.size() + " passes");
				if (!pack.getCustomTextureDataMap().getOrDefault(net.irisshaders.iris.shaderpack.texture.TextureStage.DEFERRED, it.unimi.dsi.fastutil.objects.Object2ObjectMaps.emptyMap()).isEmpty()) {
					List<String> bound = passes.stream().filter(pass -> pass.label().startsWith("Iris Vulkan deferred"))
						.flatMap(pass -> labels(pass.sampled()).stream()).filter(label -> label.startsWith("iris vulkan custom texture DEFERRED.")).distinct().toList();
					expect(packName + ": deferred passes sample the pack's texture.deferred.* textures", !bound.isEmpty(), "");
					System.out.println("  deferred custom textures           " + (bound.isEmpty() ? "FAIL none sampled" : "OK  " + bound));
				}
			} catch (RuntimeException e) {
				expect(packName + ": frame passes RenderPearl validation", false, e.toString());
				System.out.println("  frame (no terrain drawn)           FAIL " + e);
			}
			checkUsage();

			GpuTexture colortex = device.createTexture("merge check colortex0", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
				GpuFormat.RGBA16_FLOAT, main.width, main.height, 1, 1);
			GpuTexture depth = device.createTexture("merge check depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
				main.getDepthTexture().getFormat(), main.width, main.height, 1, 1);
			GpuTexture sceneDepth = device.createTexture("merge check scene depth", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
				main.getDepthTexture().getFormat(), main.width, main.height, 1, 1);
			GpuTextureView colortexView = device.createTextureView(colortex);
			GpuTextureView depthView = device.createTextureView(depth);
			GpuTextureView sceneDepthView = device.createTextureView(sceneDepth);
			passes.clear();
			try (IrisVulkanMerge merge = new IrisVulkanMerge()) {
				merge.terrainIntoScene(encoder, colortexView, depthView, main.getColorTextureView());
				merge.sceneOverTerrain(encoder, main.getColorTextureView(), depthView, depthView, depthView, colortexView);
				merge.depthWithHand(encoder, depthView, depthView, sceneDepthView);
				expect(packName + ": merge passes pass RenderPearl validation", passes.size() == 3, "passes=" + passes.size());
				System.out.println("  merge passes                       OK  " + passes.stream().map(PassRecord::label).toList());
			} catch (RuntimeException e) {
				expect(packName + ": merge passes pass RenderPearl validation", false, e.toString());
				System.out.println("  merge passes                       FAIL " + e);
			}
			checkUsage();
			colortexView.close();
			depthView.close();
			sceneDepthView.close();
			colortex.close();
			depth.close();
			sceneDepth.close();

			screenPasses.close();
			List<String> leaked = textures.subList(texturesBeforeBuild, textures.size()).stream()
				.filter(texture -> !texture.isClosed()).map(GpuTexture::getLabel).collect(Collectors.toList());
			expect(packName + ": close() releases every texture it created", leaked.isEmpty(), "still open: " + leaked);
			System.out.println("  close                              " + (leaked.isEmpty() ? "OK  all textures released" : "FAIL open: " + leaked));
		} finally {
			if (zip != null) {
				zip.close();
			}
		}
	}

	private void expectBuildFailureReleasesResources(int texturesBefore, int viewsBefore) {
		List<String> leaked = textures.subList(texturesBefore, textures.size()).stream()
			.filter(texture -> !texture.isClosed()).map(GpuTexture::getLabel).collect(Collectors.toList());
		expect("a failed build releases what it created", leaked.isEmpty(), "still open: " + leaked);
	}

	/** Usage rules for every recorded operation of the last frame. */
	private void checkUsage() {
		for (PassRecord pass : passes) {
			for (GpuTexture attachment : pass.attachments) {
				expect(pass.label + ": attachment " + attachment.getLabel() + " has USAGE_RENDER_ATTACHMENT", (attachment.usage() & GpuTexture.USAGE_RENDER_ATTACHMENT) != 0, "");
			}
			for (GpuTexture sampled : pass.sampled) {
				expect(pass.label + ": sampled " + sampled.getLabel() + " has USAGE_TEXTURE_BINDING", (sampled.usage() & GpuTexture.USAGE_TEXTURE_BINDING) != 0, "");
				expect(pass.label + ": does not sample its own attachment " + sampled.getLabel(), !pass.attachments.contains(sampled), "");
			}
			for (GpuBuffer buffer : pass.uniformBuffers) {
				expect(pass.label + ": uniform block buffer has USAGE_UNIFORM", (buffer.usage() & GpuBuffer.USAGE_UNIFORM) != 0, "");
			}
			expect(pass.label + ": draws once", pass.draws == 1, "draws=" + pass.draws);
		}
	}

	/**
	 * Every pass renders the whole target: its render area (RenderPearl's scissor; the Vulkan viewport is the
	 * attachment size) starts at the origin and spans the attachments, which all have the main target's size.
	 */
	private void checkCoverage(MainTarget main) {
		for (PassRecord pass : passes) {
			RenderPass.RenderArea area = pass.renderArea;
			for (GpuTexture attachment : pass.attachments) {
				expect(pass.label + ": attachment " + attachment.getLabel() + " is " + main.width + "x" + main.height,
					attachment.getWidth(0) == main.width && attachment.getHeight(0) == main.height, attachment.getWidth(0) + "x" + attachment.getHeight(0));
			}
			expect(pass.label + ": render area covers the whole target",
				area.x() == 0 && area.y() == 0 && area.width() == main.width && area.height() == main.height, String.valueOf(area));
		}
		System.out.println("  coverage                           " + passes.size() + " passes, render area " + passes.stream().map(pass -> pass.renderArea.width() + "x" + pass.renderArea.height()).distinct().toList() + " on a " + main.width + "x" + main.height + " target");
	}

	/** The full-screen pack: composite needs no input and writes colortex1 alt; final reads it and the scene. */
	private void checkFullscreenRouting(MainTarget main) {
		PassRecord scene = pass("Iris Vulkan scene to colortex0");
		PassRecord composite = pass("Iris Vulkan composite");
		PassRecord finalPass = pass("Iris Vulkan final");
		expect("fullscreen: frame runs scene copy, composite, final in order",
			scene != null && composite != null && finalPass != null && passes.indexOf(scene) < passes.indexOf(composite) && passes.indexOf(composite) < passes.indexOf(finalPass),
			"passes=" + passes.stream().map(PassRecord::label).toList());
		if (scene == null || composite == null || finalPass == null) {
			return;
		}

		expect("fullscreen: composite samples nothing and writes colortex1 alt",
			composite.sampled.isEmpty() && labels(composite.attachments).equals(List.of("iris colortex1 alt")), describe(composite));
		expect("fullscreen: final samples colortex1 alt and colortex0 main and writes the main target",
			Set.copyOf(labels(finalPass.sampled)).equals(Set.of("iris colortex0 main", "iris colortex1 alt")) && finalPass.attachments.equals(List.of(main.getColorTexture())),
			describe(finalPass));
		System.out.println("  routing                            " + describe(scene) + " | " + describe(composite) + " | " + describe(finalPass));
	}

	/** The CPU models IrisVulkanPixelCheck compares in-game samples with, and the sample grid itself. */
	private void checkPixelModel() {
		System.out.println("== Pixel check model and sample grid");
		for (int[] size : new int[][]{{946, 1028}, {854, 480}, {1920, 1080}, {3, 2}, {1, 1}}) {
			int width = size[0];
			int height = size[1];
			int[][] grid = IrisVulkanPixelModel.samplePositions(width, height);
			boolean inBounds = java.util.Arrays.stream(grid).allMatch(p -> p[0] >= 0 && p[0] < width && p[1] >= 0 && p[1] < height);
			expect("grid " + width + "x" + height + " stays inside the target", inBounds, "");
			for (int[] required : new int[][]{{0, 0}, {width - 1, 0}, {0, height - 1}, {width - 1, height - 1}, {width / 2, height / 2}, {0, height / 2}, {width - 1, height / 2}, {width / 2, 0}, {width / 2, height - 1}}) {
				expect("grid " + width + "x" + height + " samples (" + required[0] + "," + required[1] + ")",
					java.util.Arrays.stream(grid).anyMatch(p -> p[0] == required[0] && p[1] == required[1]), "");
			}
		}
		expect("a 1920x1080 grid has 25 distinct samples", IrisVulkanPixelModel.samplePositions(1920, 1080).length == 25, "");

		int w = 946;
		int h = 1028;
		int[] scene = {200, 100, 50};
		int gray = Math.round((0.299f * 200 + 0.587f * 100 + 0.114f * 50));
		expectRgb("smoke: bottom-left corner is marker A", IrisVulkanPixelModel.smoke(0, 0, w, h, scene), new int[]{64, 128, 191});
		expectRgb("smoke: bottom-right corner is marker B", IrisVulkanPixelModel.smoke(w - 1, 0, w, h, scene), new int[]{128, 64, 255});
		expectRgb("smoke: top-right corner is marker C", IrisVulkanPixelModel.smoke(w - 1, h - 1, w, h, scene), new int[]{Math.round(w / 4096.0f * 255), Math.round(h / 4096.0f * 255), 255});
		expectRgb("smoke: top-left corner is the grayscale half", IrisVulkanPixelModel.smoke(0, h - 1, w, h, scene), new int[]{gray, gray, gray});
		expectRgb("smoke: last column left of the split is grayscale", IrisVulkanPixelModel.smoke(w / 2 - 1, h / 2, w, h, scene), new int[]{gray, gray, gray});
		expectRgb("smoke: centre column is the scene (split at texcoord.x = 0.5)", IrisVulkanPixelModel.smoke(w / 2, h / 2, w, h, scene), scene);
		expectRgb("fullscreen: bottom-left is (u,v) near 0 plus luminance", IrisVulkanPixelModel.fullscreen(0, 0, w, h, scene), new int[]{0, 0, gray});
		expectRgb("fullscreen: top-right is (u,v) near 1", IrisVulkanPixelModel.fullscreen(w - 1, h - 1, w, h, scene), new int[]{255, 255, gray});
		expectRgb("fullscreen: centre is (u,v) = 0.5", IrisVulkanPixelModel.fullscreen(w / 2, h / 2, w, h, scene), new int[]{128, 128, gray});
		expect("a model mismatch beyond tolerance is reported", !IrisVulkanPixelModel.matches(new int[]{10, 10, 10}, new int[]{13, 10, 10}), "");
		System.out.println("  grid and models                    OK checks run");
	}

	private void expectRgb(String label, int[] actual, int[] expected) {
		expect(label, IrisVulkanPixelModel.matches(expected, actual), java.util.Arrays.toString(actual) + " vs " + java.util.Arrays.toString(expected));
	}

	/** The smoke pack's frame, per Iris's flip rules: composite writes colortex1 alt, final reads it and writes the screen. */
	private void checkSmokeRouting(MainTarget main) {
		PassRecord scene = pass("Iris Vulkan scene to colortex0");
		PassRecord composite = pass("Iris Vulkan composite");
		PassRecord finalPass = pass("Iris Vulkan final");
		expect("frame runs scene copy, composite, final in order",
			scene != null && composite != null && finalPass != null && passes.indexOf(scene) < passes.indexOf(composite) && passes.indexOf(composite) < passes.indexOf(finalPass),
			"passes=" + passes.stream().map(PassRecord::label).toList());
		if (scene == null || composite == null || finalPass == null) {
			return;
		}

		expect("scene copy samples the main target and writes colortex0 main",
			scene.sampled.equals(List.of(main.getColorTexture())) && labels(scene.attachments).equals(List.of("iris colortex0 main")), describe(scene));
		expect("composite samples colortex0 main and writes colortex1 alt",
			labels(composite.sampled).equals(List.of("iris colortex0 main")) && labels(composite.attachments).equals(List.of("iris colortex1 alt")), describe(composite));
		expect("final samples colortex0 main and colortex1 alt and writes the main target",
			Set.copyOf(labels(finalPass.sampled)).equals(Set.of("iris colortex0 main", "iris colortex1 alt")) && finalPass.attachments.equals(List.of(main.getColorTexture())),
			describe(finalPass));
		expect("final and composite each bind a uniform block", composite.uniformBuffers.size() == 1 && finalPass.uniformBuffers.size() == 1, describe(composite) + " " + describe(finalPass));
		expect("colortex1 (clear=true) is cleared each frame, main and alt", clears.containsAll(List.of("iris colortex1 main", "iris colortex1 alt")), "clears=" + clears);
		System.out.println("  routing                            " + describe(scene) + " | " + describe(composite) + " | " + describe(finalPass));
	}

	private PassRecord pass(String label) {
		return passes.stream().filter(pass -> pass.label.equals(label)).findFirst().orElse(null);
	}

	private static List<String> labels(List<GpuTexture> textures) {
		return textures.stream().map(GpuTexture::getLabel).toList();
	}

	private static String describe(PassRecord pass) {
		return pass.label.replace("Iris Vulkan ", "") + ": " + labels(pass.sampled) + " -> " + labels(pass.attachments);
	}

	private void expect(String label, boolean condition, String detail) {
		checks++;
		if (!condition) {
			failure.accept("frontend: " + label + (detail.isEmpty() ? "" : " (" + detail + ")"));
		}
	}

	// ---- Recording backend: accepts everything the frontend lets through and remembers what it was asked to do.

	private GpuDeviceBackend backend() {
		CommandEncoderBackend encoder = proxy(CommandEncoderBackend.class, (method, args) -> switch (method) {
			case "createRenderPass" -> {
				RenderPassDescriptor descriptor = (RenderPassDescriptor) args[0];
				List<GpuTexture> attachments = descriptor.colorAttachments().stream().map(attachment -> attachment.textureView().texture()).toList();
				currentPass = new PassRecord(descriptor.label().get(), attachments, descriptor.renderArea(), new ArrayList<>(), new ArrayList<>(), 0);
				passes.add(currentPass);
				yield renderPass();
			}
			case "clearColorTexture" -> {
				clears.add(((GpuTexture) args[0]).getLabel());
				yield null;
			}
			case "transientMemory" -> transientMemory();
			default -> null;
		});

		return proxy(GpuDeviceBackend.class, (method, args) -> switch (method) {
			case "createCommandEncoder" -> encoder;
			case "createTexture" -> {
				RecordingTexture texture = new RecordingTexture((int) args[1], (String) args[0], (GpuFormat) args[2], (int) args[3], (int) args[4], (int) args[5], (int) args[6]);
				textures.add(texture);
				yield texture;
			}
			case "createTextureView" -> {
				RecordingView view = new RecordingView((GpuTexture) args[0], (int) args[1], (int) args[2]);
				views.add(view);
				yield view;
			}
			case "createBuffer" -> new RecordingBuffer((int) args[1], args[2] instanceof ByteBuffer data ? data.remaining() : (long) args[2]);
			case "createSampler" -> proxy(com.mojang.renderpearl.api.textures.GpuSampler.class, (m, a) -> null);
			case "compilePipeline" -> (BackendRenderPipeline.Pending) () -> proxy(BackendRenderPipeline.class, (m, a) -> null);
			case "getDeviceInfo" -> DEVICE_INFO;
			case "getLastDebugMessages" -> List.of();
			// Like a debug session: the frontend only forwards texture labels when debugging is enabled.
			case "isDebuggingEnabled" -> true;
			case "getTimestampCalibrationOffset" -> 0L;
			default -> null;
		});
	}

	private RenderPassBackend renderPass() {
		return proxy(RenderPassBackend.class, (method, args) -> {
			PassRecord pass = currentPass;
			switch (method) {
				case "setUniform" -> {
					if (args[1] instanceof TextureViewAndSampler bound) {
						pass.sampled.add(bound.view().texture());
					} else if (args[1] instanceof GpuBufferSlice slice) {
						pass.uniformBuffers.add(slice.buffer());
					}
				}
				case "draw", "drawIndexed" -> currentPass = new PassRecord(pass.label, pass.attachments, pass.renderArea, pass.sampled, pass.uniformBuffers, pass.draws + 1);
				default -> {
				}
			}
			if (currentPass != pass) {
				passes.set(passes.indexOf(pass), currentPass);
			}
			return null;
		});
	}

	private TransientMemory transientMemory() {
		return proxy(TransientMemory.class, (method, args) -> switch (method) {
			case "uploadGpu", "uploadStaging" -> {
				@SuppressWarnings("unchecked")
				List<ByteBuffer> data = (List<ByteBuffer>) args[0];
				long size = data.stream().mapToLong(ByteBuffer::remaining).sum();
				yield new GpuBufferSlice(new RecordingBuffer((int) args[2], size), 0, size);
			}
			default -> throw new UnsupportedOperationException("recording backend: TransientMemory." + method);
		});
	}

	private interface Handler {
		Object handle(String method, Object[] args) throws Throwable;
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Handler handler) {
		InvocationHandler invocation = (proxy, method, args) -> {
			if (method.isDefault()) {
				return InvocationHandler.invokeDefault(proxy, method, args);
			}
			Object result = switch (method.getName()) {
				case "isClosed" -> false;
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				case "toString" -> type.getSimpleName() + "@recording";
				default -> handler.handle(method.getName(), args == null ? new Object[0] : args);
			};
			return result != null || !method.getReturnType().isPrimitive() ? result : primitiveDefault(method.getReturnType());
		};
		return (T) Proxy.newProxyInstance(VulkanFrontendCheck.class.getClassLoader(), new Class<?>[]{type}, invocation);
	}

	private static Object primitiveDefault(Class<?> type) {
		if (type == boolean.class) {
			return false;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0.0f;
		}
		if (type == double.class) {
			return 0.0;
		}
		return type == void.class ? null : 0;
	}

	private static final class RecordingTexture extends BaseGpuTexture {
		private boolean closed;

		RecordingTexture(int usage, String label, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels) {
			super(usage, label, format, width, height, depthOrLayers, mipLevels);
		}

		@Override
		public boolean isClosed() {
			return closed;
		}

		@Override
		public void close() {
			closed = true;
		}
	}

	private static final class RecordingView extends BaseGpuTextureView {
		private boolean closed;

		RecordingView(GpuTexture texture, int baseMipLevel, int mipLevels) {
			super(texture, baseMipLevel, mipLevels);
		}

		@Override
		public boolean isClosed() {
			return closed;
		}

		@Override
		public void close() {
			closed = true;
		}
	}

	private static final class RecordingBuffer extends BaseGpuBuffer {
		private boolean closed;

		RecordingBuffer(int usage, long size) {
			super(usage, size);
		}

		@Override
		public boolean isClosed() {
			return closed;
		}

		@Override
		public void close() {
			closed = true;
		}

		@Override
		public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
			ByteBuffer data = MemoryUtil.memCalloc((int) length);
			return new GpuBufferSlice.MappedView(slice(offset, length), data, () -> MemoryUtil.memFree(data));
		}
	}

	private static final class Field {
		static void set(Class<?> owner, String name, Object value) throws ReflectiveOperationException {
			java.lang.reflect.Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			field.set(null, value);
		}
	}
}
