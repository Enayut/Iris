package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4fc;

/**
 * Phase 4: per-frame snapshot of Iris shaderpack uniforms for the Vulkan
 * screen-pass chain. The registered suppliers (same values the OpenGL
 * {@code ProgramUniforms} consumes) are sampled once per frame into a single
 * uniform block, uploaded through the renderpearl {@code CommandEncoder} into
 * a {@link GpuBuffer} with {@code USAGE_UNIFORM} (a real Vulkan UBO), and
 * bound by name in screen passes.
 *
 * <p>Layout: each uniform gets a fixed 256-byte slot (16-byte aligned;
 * matrices stored column-major). The Vulkan GLSL preamble emitted by the
 * shader translation layer reads uniforms from this block at
 * {@code slot * 256} byte offsets.</p>
 */
public final class IrisVulkanUniformSnapshot {
	/** 256 bytes per uniform slot: matrices fit exactly, scalars/vectors padded. */
	public static final int SLOT_SIZE = 256;
	private static final int MAX_UNIFORMS = 256;

	private final List<Entry> entries = new ArrayList<>();
	private final GpuBuffer buffer;
	private final ByteBuffer staging;

	public IrisVulkanUniformSnapshot() {
		staging = ByteBuffer.allocateDirect(SLOT_SIZE * MAX_UNIFORMS).order(ByteOrder.nativeOrder());
		buffer = RenderSystem.getDevice().createBuffer(
			() -> "Iris Vulkan uniform block",
			GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
			SLOT_SIZE * MAX_UNIFORMS
		);
	}

	/** Registers the core Iris dynamic uniform set (same values as GL screen passes). */
	public void registerCommonUniforms() {
		addMatrix("gbufferModelView", CapturedRenderingState.INSTANCE::getGbufferModelView);
		addMatrix("gbufferProjection", CapturedRenderingState.INSTANCE::getGbufferProjection);
	}

	private void addMatrix(String name, Supplier<Matrix4fc> supplier) {
		final int slot = entries.size();
		entries.add(new Entry(name, slot, buf -> {
			Matrix4fc m = supplier.get();
			if (m != null) {
				m.get(buf);
			}
		}));
	}

	private void addFloat(String name, FloatSupplier supplier) {
		final int slot = entries.size();
		entries.add(new Entry(name, slot, buf -> buf.putFloat(supplier.getAsFloat())));
	}

	/** Samples all registered suppliers and uploads the block to the GPU. */
	public void update() {
		staging.clear();
		for (Entry entry : entries) {
			staging.position(entry.slot * SLOT_SIZE);
			staging.limit(entry.slot * SLOT_SIZE + SLOT_SIZE);
			entry.writer.accept(staging.slice());
		}

		staging.position(0);
		staging.limit(SLOT_SIZE * MAX_UNIFORMS);
		staging.rewind();

		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, SLOT_SIZE * MAX_UNIFORMS), staging);
	}

	public GpuBuffer buffer() {
		return buffer;
	}

	public GpuBufferSlice slice() {
		return buffer.slice(0, SLOT_SIZE * MAX_UNIFORMS);
	}

	public void destroy() {
		buffer.close();
		entries.clear();
	}

	private record Entry(String name, int slot, java.util.function.Consumer<ByteBuffer> writer) {
	}
}
