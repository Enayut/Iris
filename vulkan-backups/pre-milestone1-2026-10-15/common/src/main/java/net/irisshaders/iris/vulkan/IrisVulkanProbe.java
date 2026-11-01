package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import net.irisshaders.iris.Iris;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Stage B probe: proves that this process can run a real shader through Minecraft 26.3's native
 * RenderPearl Vulkan backend, end to end, and verifies the output on the CPU.
 *
 * <ol>
 *   <li>Compiles a GLSL vertex/fragment pair to SPIR-V via {@link GpuDevice#compilePipeline} with a
 *       {@link BindGroupLayout} that declares one combined image sampler and one std140 uniform block.</li>
 *   <li>Uploads a known texel into a 4x4 input texture and a known modulation vector into a UBO.</li>
 *   <li>Draws a full-screen triangle into a 4x4 RGBA8 render target that was cleared to a sentinel colour.</li>
 *   <li>Copies the target into a mappable buffer and checks every pixel against the expected
 *       {@code texel * modulate} value once the GPU signals completion.</li>
 * </ol>
 *
 * <p>The probe is asynchronous: the readback callback is queued by the backend and runs after the
 * GPU has finished the frame, so {@link #start()} returns immediately and {@link #result()} moves from
 * {@link Result#PENDING} to {@link Result#PASS} or {@link Result#FAIL} on a later frame.</p>
 */
public final class IrisVulkanProbe {
	public enum Result {
		NOT_STARTED,
		PENDING,
		PASS,
		FAIL
	}

	private static final int SIZE = 4;
	private static final long TIMEOUT_NANOS = 10_000_000_000L;
	// Input texel (64, 128, 192, 255) * modulate (2.0, 1.0, 0.5, 1.0) = (128, 128, 96, 255).
	private static final byte[] INPUT_TEXEL = {(byte) 64, (byte) 128, (byte) 192, (byte) 255};
	private static final float[] MODULATE = {2.0f, 1.0f, 0.5f, 1.0f};
	private static final int[] EXPECTED = {128, 128, 96, 255};
	private static final int TOLERANCE = 2;

	private static final Identifier VERTEX_ID = Identifier.fromNamespaceAndPath("iris", "vulkan_probe/probe.vsh");
	private static final Identifier FRAGMENT_ID = Identifier.fromNamespaceAndPath("iris", "vulkan_probe/probe.fsh");

	// Same dialect vanilla 26.3 ships for its post-processing shaders (see minecraft:shaders/core/screenquad.vsh):
	// explicit interface locations, opaque samplers outside blocks, everything else in std140 blocks.
	static final String VERTEX_SOURCE = """
		#version 330
		#extension GL_ARB_separate_shader_objects : require

		layout(location = 0) out vec2 texCoord;

		void main() {
		    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
		    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
		    texCoord = uv;
		}
		""";

	static final String FRAGMENT_SOURCE = """
		#version 330
		#extension GL_ARB_separate_shader_objects : require

		uniform sampler2D ProbeSampler;

		layout(std140) uniform ProbeConfig {
		    vec4 Modulate;
		};

		layout(location = 0) in vec2 texCoord;

		layout(location = 0) out vec4 fragColor;

		void main() {
		    fragColor = texture(ProbeSampler, texCoord) * Modulate;
		}
		""";

	private static final IrisVulkanProbe INSTANCE = new IrisVulkanProbe();

	private volatile Result result = Result.NOT_STARTED;
	private volatile String detail = "Not run yet.";
	private long startedAt;

	private CompiledRenderPipeline pipeline;
	private GpuTexture input;
	private GpuTextureView inputView;
	private GpuTexture output;
	private GpuTextureView outputView;
	private GpuBuffer uniforms;
	private GpuBuffer readback;
	private GpuSampler sampler;

	public static IrisVulkanProbe get() {
		return INSTANCE;
	}

	public Result result() {
		if (result == Result.PENDING && System.nanoTime() - startedAt > TIMEOUT_NANOS) {
			fail("GPU readback did not complete within 10 seconds.");
		}

		return result;
	}

	public String detail() {
		return detail;
	}

	/** Starts the probe on the render thread. Does nothing if it already ran or is running. */
	public void start() {
		RenderSystem.assertOnRenderThread();
		if (result != Result.NOT_STARTED) {
			return;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			fail("No RenderPearl GpuDevice exists yet.");
			return;
		}

		if (!"Vulkan".equalsIgnoreCase(device.getDeviceInfo().backendName())) {
			fail("Active backend is " + device.getDeviceInfo().backendName() + ", not Vulkan.");
			return;
		}

		result = Result.PENDING;
		detail = "Waiting for GPU readback.";
		startedAt = System.nanoTime();

		try {
			run(device);
		} catch (Throwable t) {
			Iris.logger.error("[Iris/Vulkan] Probe failed before readback", t);
			fail(t.getClass().getSimpleName() + ": " + t.getMessage());
			// Closing is deferred by the backend until the GPU is done with anything already recorded.
			release();
		}
	}

	private void run(GpuDevice device) {
		RenderPipeline description = RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath("iris", "vulkan_probe"))
			.withVertexShader(VERTEX_ID)
			.withFragmentShader(FRAGMENT_ID)
			.withBindGroupLayout(BindGroupLayout.builder()
				.withUniform("ProbeSampler", UniformType.COMBINED_IMAGE_SAMPLER)
				.withUniform("ProbeConfig", UniformType.UNIFORM_BUFFER)
				.build())
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.build();

		// Runnable::run makes PipelineBuilder compile on this thread, so compile errors surface here.
		try (ProbeShaderSource source = new ProbeShaderSource()) {
			pipeline = device.compilePipeline(description, source, Runnable::run).join().finishCompile();
		}

		if (pipeline == null) {
			throw new IllegalStateException("GLSL -> SPIR-V compilation or Vulkan pipeline creation failed; see the PipelineBuilder error in latest.log.");
		}

		CommandEncoder encoder = device.createCommandEncoder();

		input = device.createTexture("iris vulkan probe input", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, GpuFormat.RGBA8_UNORM, SIZE, SIZE, 1, 1);
		inputView = device.createTextureView(input);
		ByteBuffer texels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
		try {
			for (int i = 0; i < SIZE * SIZE; i++) {
				texels.put(INPUT_TEXEL);
			}
			texels.flip();
			encoder.writeToTexture(input, texels, 0, 0, 0, 0, SIZE, SIZE);
		} finally {
			MemoryUtil.memFree(texels);
		}

		ByteBuffer ubo = MemoryUtil.memAlloc(16);
		try {
			for (float f : MODULATE) {
				ubo.putFloat(f);
			}
			ubo.flip();
			uniforms = device.createBuffer(() -> "iris vulkan probe uniforms", GpuBuffer.USAGE_UNIFORM, ubo);
		} finally {
			MemoryUtil.memFree(ubo);
		}

		sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());

		output = device.createTexture("iris vulkan probe output", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, SIZE, SIZE, 1, 1);
		outputView = device.createTextureView(output);

		// Sentinel clear colour (magenta) so an untouched target can never pass the check.
		try (RenderPass pass = encoder.createRenderPass(() -> "Iris Vulkan probe", outputView, Optional.of(new Vector4f(1.0f, 0.0f, 1.0f, 1.0f)))) {
			pass.setPipeline(pipeline);
			pass.setUniform("ProbeSampler", inputView, sampler);
			pass.setUniform("ProbeConfig", uniforms);
			pass.draw(3, 1, 0, 0);
		}

		readback = device.createBuffer(() -> "iris vulkan probe readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) SIZE * SIZE * 4);
		encoder.copyTextureToBuffer(output, readback, 0, this::verify, 0);
	}

	private void verify() {
		if (result != Result.PENDING) {
			release();
			return;
		}

		try (GpuBufferSlice.MappedView view = readback.map(true, false)) {
			ByteBuffer data = view.data();
			for (int pixel = 0; pixel < SIZE * SIZE; pixel++) {
				for (int c = 0; c < 4; c++) {
					int actual = data.get(pixel * 4 + c) & 0xFF;
					if (Math.abs(actual - EXPECTED[c]) > TOLERANCE) {
						fail(String.format(java.util.Locale.ROOT, "Pixel %d channel %d is %d, expected %d (sentinel clear is 255,0,255,255).", pixel, c, actual, EXPECTED[c]));
						return;
					}
				}
			}

			result = Result.PASS;
			detail = "Compiled GLSL to SPIR-V, bound a sampler and a UBO, drew to a 4x4 RGBA8 target and verified all 16 pixels on readback.";
			Iris.logger.info("[Iris/Vulkan] Probe PASS: {}", detail);
		} catch (Throwable t) {
			Iris.logger.error("[Iris/Vulkan] Probe readback failed", t);
			fail(t.getClass().getSimpleName() + ": " + t.getMessage());
		} finally {
			release();
		}
	}

	private void fail(String reason) {
		result = Result.FAIL;
		detail = reason;
		Iris.logger.error("[Iris/Vulkan] Probe FAIL: {}", reason);
	}

	private void release() {
		if (readback != null) {
			readback.close();
			readback = null;
		}

		if (outputView != null) {
			outputView.close();
			outputView = null;
		}

		if (output != null) {
			output.close();
			output = null;
		}

		if (inputView != null) {
			inputView.close();
			inputView = null;
		}

		if (input != null) {
			input.close();
			input = null;
		}

		if (uniforms != null) {
			uniforms.close();
			uniforms = null;
		}

		if (sampler != null) {
			sampler.close();
			sampler = null;
		}

		if (pipeline != null) {
			pipeline.close();
			pipeline = null;
		}
	}

	private static final class ProbeShaderSource implements ShaderSource {
		@Override
		public String getShader(Identifier id, ShaderType type) {
			if (id.equals(VERTEX_ID) && type == ShaderType.VERTEX) {
				return VERTEX_SOURCE;
			}

			if (id.equals(FRAGMENT_ID) && type == ShaderType.FRAGMENT) {
				return FRAGMENT_SOURCE;
			}

			return null;
		}

		@Override
		public CachedIncludeSource getInclude(Identifier id) {
			return null;
		}

		@Override
		public void close() {
		}
	}

	private IrisVulkanProbe() {
	}
}
