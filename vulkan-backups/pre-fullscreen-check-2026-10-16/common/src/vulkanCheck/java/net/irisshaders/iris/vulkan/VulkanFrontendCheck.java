package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.TransientMemory;
import com.mojang.renderpearl.api.commands.CommandEncoder;
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

	record PassRecord(String label, List<GpuTexture> attachments, List<GpuTexture> sampled, List<GpuBuffer> uniformBuffers, int draws) {
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
	static int run(Path smokePack, Path work, Consumer<String> failure) throws Exception {
		VulkanFrontendCheck check = new VulkanFrontendCheck(failure);
		check.runSmokePack(smokePack);
		check.runFailingBuild(work);
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

	private void runSmokePack(Path smokePack) throws Exception {
		System.out.println("== RenderPearl frontend validation (IrisVulkanSmoke)");
		FrontendGpuDevice device = new FrontendGpuDevice(backend());
		Field.set(RenderSystem.class, "DEVICE", device);
		if (!RenderSystem.isOnRenderThread()) {
			RenderSystem.initRenderThread();
		}

		ShaderPack pack = new ShaderPack(smokePack.resolve("shaders"), Map.of(), StandardMacros.createStandardEnvironmentDefines(), false);
		MainTarget main = new MainTarget(854, 480);
		int texturesBeforeBuild = textures.size();
		int viewsBeforeBuild = views.size();

		FrameUpdateNotifier notifier = new FrameUpdateNotifier();
		IrisVulkanUniforms uniforms = new IrisVulkanUniforms();
		uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "viewWidth", () -> (float) main.width);
		uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "viewHeight", () -> (float) main.height);

		IrisVulkanScreenPasses screenPasses;
		try {
			screenPasses = new IrisVulkanScreenPasses("frontendcheck", pack.getProgramSet(DimensionId.OVERWORLD), uniforms, notifier, main.width, main.height);
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
		checkSmokeRouting(main);

		screenPasses.close();
		List<String> leaked = textures.subList(texturesBeforeBuild, textures.size()).stream()
			.filter(texture -> !texture.isClosed()).map(GpuTexture::getLabel).collect(Collectors.toList());
		List<String> leakedViews = views.subList(viewsBeforeBuild, views.size()).stream()
			.filter(view -> !view.isClosed()).map(view -> view.texture().getLabel()).collect(Collectors.toList());
		expect("close() releases every texture it created", leaked.isEmpty(), "still open: " + leaked);
		expect("close() releases every texture view it created", leakedViews.isEmpty(), "still open: " + leakedViews);
		System.out.println("  close                              " + (leaked.isEmpty() && leakedViews.isEmpty() ? "OK  all textures and views released" : "FAIL open: " + leaked + leakedViews));
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
				currentPass = new PassRecord(descriptor.label().get(), attachments, new ArrayList<>(), new ArrayList<>(), 0);
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
				case "draw", "drawIndexed" -> currentPass = new PassRecord(pass.label, pass.attachments, pass.sampled, pass.uniformBuffers, pass.draws + 1);
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
