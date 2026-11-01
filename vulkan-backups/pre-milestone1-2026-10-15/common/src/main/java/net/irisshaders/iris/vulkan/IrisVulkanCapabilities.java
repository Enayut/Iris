package net.irisshaders.iris.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.GpuDevice;
import java.util.ArrayList;
import java.util.List;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.mixin.IrisMixinPlugin;
import net.minecraft.client.Minecraft;

/**
 * Explicit capability check for running Iris shader packs on Minecraft 26.3's native Vulkan backend.
 *
 * <p>This deliberately separates three questions that the old guard conflated:</p>
 * <ul>
 *   <li>Did the player <em>ask</em> for Vulkan? ({@code preferredGraphicsBackend} in options.txt, which is all
 *       {@link IrisMixinPlugin} can see when it decides which mixins to apply.)</li>
 *   <li>Did Minecraft actually <em>create</em> a Vulkan device? ({@link DeviceInfo#backendName()}; RenderPearl
 *       falls back to the other API if the preferred one fails, so these can disagree.)</li>
 *   <li>Can Iris actually <em>render a shader pack</em> on that device? Only true when the probe passes and
 *       every component of the shader pack pipeline has a Vulkan implementation.</li>
 * </ul>
 *
 * <p>{@link #isShaderpackRenderingAvailable()} is the only thing that may lift the Vulkan guard.</p>
 */
public final class IrisVulkanCapabilities {
	public enum Status {
		PASS,
		FAIL,
		RUNNING,
		MISSING,
		INFO
	}

	public record Check(String name, Status status, String detail) {
		public boolean blocksShaderpacks() {
			return status == Status.FAIL || status == Status.RUNNING || status == Status.MISSING;
		}
	}

	/**
	 * Components of the shader pack pipeline that have no working Vulkan implementation yet. An entry may only be
	 * removed once its replacement is wired into the live pipeline and has been verified at runtime on Vulkan.
	 */
	private static final List<Check> MISSING_COMPONENTS = List.of(
		new Check("Iris world-rendering hooks", Status.MISSING,
			"IrisMixinPlugin disables every non-VKOnly mixin when Vulkan is selected, including Iris initialisation "
				+ "(MixinOptions_Entrypoint), so no IrisRenderingPipeline is ever created."),
		new Check("Gbuffer/shadow program override", Status.MISSING,
			"MixinShaderManager_Overrides builds GlRenderPipeline objects from linked GlProgram objects; "
				+ "this path only exists in the OpenGL backend."),
		new Check("Shader pack GLSL for Vulkan", Status.MISSING,
			"IrisVulkanGlslTranslator converts TransformPatcher output (std140 uniform block, explicit locations, derived "
				+ "sampler list) and passes offline shaderc + reflection checks on Complementary's composite/final passes, "
				+ "but is not yet wired into a live Vulkan pipeline."),
		new Check("Render targets and composite/final passes", Status.MISSING,
			"RenderTargets, CompositeRenderer, FinalPassRenderer and ShadowRenderer use GL framebuffers and textures. "
				+ "The IrisVulkanRenderer chain is only reachable from IrisRenderingPipeline and is never run."),
		new Check("RenderPearl API coverage", Status.MISSING,
			"No compute or geometry stages, storage images, SSBOs, depth-compare samplers, or 1D/3D sampled "
				+ "textures are exposed; shader packs that use them need another strategy.")
	);

	private static boolean reportLogged;

	private IrisVulkanCapabilities() {
	}

	/** True only when every check passes, i.e. Iris can genuinely render shader packs through Vulkan. */
	public static boolean isShaderpackRenderingAvailable() {
		return evaluate().stream().noneMatch(Check::blocksShaderpacks);
	}

	public static List<Check> evaluate() {
		List<Check> checks = new ArrayList<>();
		GpuDevice device = RenderSystem.tryGetDevice();
		String requested = Minecraft.getInstance().options.preferredGraphicsBackend().get().getSerializedName();
		String mixinMode = IrisMixinPlugin.usingVulkan ? "Vulkan-only (Iris disabled)" : "OpenGL (full Iris)";

		if (device == null) {
			checks.add(new Check("Graphics backend", Status.RUNNING, "No GPU device yet."));
		} else {
			DeviceInfo info = device.getDeviceInfo();
			boolean vulkan = "Vulkan".equalsIgnoreCase(info.backendName());
			boolean consistent = vulkan == IrisMixinPlugin.usingVulkan;
			checks.add(new Check("Graphics backend", consistent ? Status.INFO : Status.FAIL,
				"Requested " + requested + ", active " + info.backendName() + ", Iris mixins " + mixinMode
					+ (consistent ? "." : ". The active backend differs from the one Iris was configured for at launch; restart the game.")));
			checks.add(new Check("GPU", Status.INFO, info.name() + " (" + info.type() + "), " + info.driverInfo()));
		}

		IrisVulkanProbe probe = IrisVulkanProbe.get();
		Status probeStatus = switch (probe.result()) {
			case PASS -> Status.PASS;
			case FAIL -> Status.FAIL;
			case PENDING, NOT_STARTED -> Status.RUNNING;
		};
		checks.add(new Check("Vulkan test pipeline", probeStatus, probe.detail()));
		checks.addAll(MISSING_COMPONENTS);
		return checks;
	}

	/**
	 * Called once per client tick in Vulkan mode. Starts the probe once the title screen is up and logs the
	 * report to latest.log when the probe finishes.
	 */
	public static void tick() {
		IrisVulkanProbe probe = IrisVulkanProbe.get();
		if (probe.result() == IrisVulkanProbe.Result.NOT_STARTED) {
			if (RenderSystem.tryGetDevice() != null && Minecraft.getInstance().gui.screen() != null) {
				probe.start();
			}
			return;
		}

		if (!reportLogged && probe.result() != IrisVulkanProbe.Result.PENDING) {
			reportLogged = true;
			List<Check> checks = evaluate();
			Iris.logger.info("[Iris/Vulkan] Capability report (shader packs available on Vulkan: {}):", checks.stream().noneMatch(Check::blocksShaderpacks));
			for (Check check : checks) {
				Iris.logger.info("[Iris/Vulkan]   [{}] {}: {}", check.status(), check.name(), check.detail());
			}
		}
	}
}
