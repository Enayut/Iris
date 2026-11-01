package net.irisshaders.iris.vulkan;

import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.util.spvc.Spvc.*;

import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.DeviceType;
import com.mojang.renderpearl.api.device.GpuDevice;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.config.IrisConfig;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.loading.ProgramArrayId;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.vertices.sodium.terrain.FormatAnalyzer;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.spvc.SpvcReflectedResource;

/**
 * Offline validation of the Vulkan screen-pass shader path ({@code ./gradlew :common:vulkanShaderCheck}).
 *
 * <p>Shader packs are loaded by Iris's real {@link ShaderPack} loader and transformed by the production
 * {@link IrisVulkanScreenPasses#transform} and {@link IrisVulkanProgram#prepare}. The result is compiled with the
 * shaderc options RenderPearl 26.3's GlslCompiler uses, reflected with SPIRV-Cross like its SPIRVModule, and held
 * to PipelineBuilder's link rules plus the std140 offsets IrisVulkanUniforms writes. Negative tests check that
 * unsupported GLSL is rejected with a clear error.</p>
 *
 * <p>This proves the shaders are accepted by the Vulkan toolchain. It does not prove that anything renders
 * correctly; that is the in-game pixel check's job.</p>
 */
public final class VulkanShaderCheck {
	private static final Pattern UNIFORM = Pattern.compile("^uniform (\\w+) (\\w+)\\s*(=[^;]*)?;", Pattern.MULTILINE);
	private static final ProgramArrayId[] STAGES = {ProgramArrayId.Begin, ProgramArrayId.Prepare, ProgramArrayId.Deferred, ProgramArrayId.Composite};

	private record Var(String name, int location, int baseType, int vectorSize, boolean flat) {
	}

	private record Reflection(List<Var> inputs, List<Var> outputs, Set<String> descriptors, Map<String, Integer> blockOffsets, int pushConstants, int pushConstantBytes) {
	}

	private final List<String> failures = new ArrayList<>();
	private final long compiler = shaderc_compiler_initialize();
	private int checks;

	public static void main(String[] args) throws Exception {
		Path smokePack = Path.of(args[0]);
		Path work = Path.of(args[1]);
		List<Path> extraPacks = new ArrayList<>();
		for (int i = 2; i < args.length; i++) {
			if (!args[i].isBlank()) {
				extraPacks.add(Path.of(args[i]));
			}
		}

		setUpEnvironment(work);
		VulkanShaderCheck check = new VulkanShaderCheck();
		check.checkSmokePack(smokePack);
		check.checkPack(smokePack.resolveSibling("IrisVulkanFullscreen"));
		for (Path pack : extraPacks) {
			check.checkPack(pack);
		}
		check.negativeTests();
		check.checks += VulkanMixinCoverageCheck.run(check.failures::add);
		// Last: it replaces the stand-in device with RenderPearl's real frontend over a recording backend.
		check.checks += VulkanFrontendCheck.run(smokePack, work, extraPacks, check.failures::add);

		System.out.printf("%nvulkanShaderCheck: %d checks, %d failures%n", check.checks, check.failures.size());
		check.failures.forEach(failure -> System.out.println("  FAIL " + failure));
		System.exit(check.failures.isEmpty() ? 0 : 1);
	}

	/** What Iris needs from the game for pack loading and patching, with the values a Vulkan session provides. */
	private static void setUpEnvironment(Path work) throws Exception {
		System.setProperty("iris.vulkanCheck.dir", work.toString());
		Files.createDirectories(work.resolve("config"));
		net.minecraft.SharedConstants.tryDetectVersion();
		setStatic(Iris.class, "IRIS_VERSION", "1.11.4");
		setStatic(Iris.class, "irisConfig", new IrisConfig(work.resolve("config/iris.properties"), work.resolve("config/iris-excluded.json")));
		// Only getDeviceInfo() is answered: StandardMacros, FeatureFlags and TransformPatcher read the backend name
		// and isZZeroToOne(), which for RenderPearl's Vulkan backend are "Vulkan" and true.
		DeviceInfo info = new DeviceInfo("NVIDIA GeForce RTX 3050 Laptop GPU", "NVIDIA", "vulkanShaderCheck", true, "Vulkan", 1.0f, null, null, Set.of(), null, DeviceType.DISCRETE);
		Object device = Proxy.newProxyInstance(VulkanShaderCheck.class.getClassLoader(), new Class<?>[]{GpuDevice.class}, (proxy, method, methodArgs) -> {
			if (method.getName().equals("getDeviceInfo")) {
				return info;
			}
			throw new UnsupportedOperationException("vulkanShaderCheck has no GPU: " + method.getName());
		});
		setStatic(com.mojang.blaze3d.systems.RenderSystem.class, "DEVICE", device);
	}

	private static void setStatic(Class<?> owner, String name, Object value) throws ReflectiveOperationException {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}

	private void checkSmokePack(Path packDir) throws IOException {
		System.out.println("== IrisVulkanSmoke (" + packDir + ")");
		ShaderPack pack = new ShaderPack(packDir.resolve("shaders"), Map.of(), StandardMacros.createStandardEnvironmentDefines(), false);
		ProgramSet set = pack.getProgramSet(DimensionId.OVERWORLD);
		Map<String, IrisVulkanGlslTranslator.Result> results = checkProgramSet("IrisVulkanSmoke", set);

		expect("smoke: composite writes colortex1", java.util.Arrays.equals(set.getComposite(ProgramArrayId.Composite)[0].getDirectives().getDrawBuffers(), new int[]{1}));
		IrisVulkanGlslTranslator.Result composite = results.get("composite/all");
		IrisVulkanGlslTranslator.Result finalPass = results.get("final/all");
		expect("smoke: composite samples colortex0 only", composite != null && composite.samplers().equals(List.of("colortex0")));
		expect("smoke: final samples colortex0 and colortex1", finalPass != null && new TreeSet<>(finalPass.samplers()).equals(new TreeSet<>(List.of("colortex0", "colortex1"))));
		expect("smoke: viewWidth and viewHeight are in the uniform block", finalPass != null
			&& finalPass.uniformFields().stream().map(IrisVulkanGlslTranslator.UniformField::name).toList().containsAll(List.of("viewHeight", "viewWidth")));
	}

	private void checkPack(Path path) throws IOException {
		System.out.println("== " + path.getFileName() + " (" + path + ")");
		if (Files.isDirectory(path)) {
			ShaderPack pack = new ShaderPack(path.resolve("shaders"), Map.of(), StandardMacros.createStandardEnvironmentDefines(), false);
			checkProgramSet(path.getFileName().toString(), pack.getProgramSet(DimensionId.OVERWORLD));
			return;
		}

		try (FileSystem zip = FileSystems.newFileSystem(path)) {
			ShaderPack pack = new ShaderPack(zip.getPath("shaders"), Map.of(), StandardMacros.createStandardEnvironmentDefines(), true);
			checkProgramSet(path.getFileName().toString(), pack.getProgramSet(DimensionId.OVERWORLD));
		}
	}

	/**
	 * Every screen pass, translated twice: with every declared uniform supplied (all go into the std140 block) and
	 * with none supplied (all become defaulted globals). The live session supplies a subset, i.e. a mix of the two.
	 */
	private Map<String, IrisVulkanGlslTranslator.Result> checkProgramSet(String packName, ProgramSet set) {
		Map<String, IrisVulkanGlslTranslator.Result> results = new HashMap<>();
		List<ProgramSource> sources = new ArrayList<>();
		List<TextureStage> stages = new ArrayList<>();
		for (ProgramArrayId stage : STAGES) {
			for (ProgramSource source : set.getComposite(stage)) {
				if (source != null && source.isValid()) {
					sources.add(source);
					stages.add(IrisVulkanScreenPasses.textureStage(stage));
				}
			}
		}
		set.get(ProgramId.Final).filter(ProgramSource::isValid).ifPresent(source -> {
			sources.add(source);
			stages.add(TextureStage.COMPOSITE_AND_FINAL);
		});

		expect(packName + ": has screen passes", !sources.isEmpty());
		checkTerrain(packName, set);
		for (int i = 0; i < sources.size(); i++) {
			ProgramSource source = sources.get(i);
			Map<PatchShaderType, String> transformed = IrisVulkanScreenPasses.transform(set.getPackDirectives(), source, stages.get(i));
			for (String mode : List.of("all", "none")) {
				Map<String, String> supplied = mode.equals("all") ? declaredUniforms(transformed) : Map.of();
				String label = packName + "/" + source.getName() + "/" + mode;
				try {
					IrisVulkanGlslTranslator.Result result = IrisVulkanProgram.prepare(source.getName(), transformed, supplied);
					List<String> problems = linkCheck(source.getName(), result);
					expect(label, problems.isEmpty(), String.join("; ", problems));
					results.put(source.getName() + "/" + mode, result);
					System.out.printf("  %-34s %s  %s%n", label, problems.isEmpty() ? "OK  " : "FAIL", IrisVulkanGlslTranslator.describe(result));
				} catch (RuntimeException e) {
					expect(label, false, e.toString());
					System.out.printf("  %-34s FAIL %s%n", label, e);
				}
			}
		}

		return results;
	}

	/**
	 * The pack's gbuffers_terrain programs (solid and cutout, with Iris's fallbacks), through the production Sodium
	 * transformation and {@link IrisVulkanTerrain#prepare}, against Iris's extended Sodium vertex format.
	 */
	private void checkTerrain(String packName, ProgramSet set) {
		VertexFormat format = FormatAnalyzer.createFormat(true, true, true, true).getVertexFormat();
		ProgramFallbackResolver resolver = new ProgramFallbackResolver(set);
		for (IrisVulkanTerrain.Layer layer : IrisVulkanTerrain.Layer.values()) {
			ProgramSource source = resolver.resolveNullable(layer.programId);
			if (source == null || !source.isValid()) {
				System.out.printf("  %-34s none (terrain stays vanilla)%n", packName + "/terrain " + layer);
				continue;
			}

			Map<PatchShaderType, String> transformed = IrisVulkanTerrain.transform(set.getPackDirectives(), source, layer);
			int[] drawBuffers = source.getDirectives().getDrawBuffers();
			for (String mode : List.of("all", "none")) {
				Map<String, String> supplied = mode.equals("all") ? declaredUniforms(transformed) : Map.of();
				String label = packName + "/" + source.getName() + " " + layer + "/" + mode;
				try {
					IrisVulkanGlslTranslator.Result result = IrisVulkanTerrain.prepare(source.getName(), transformed, supplied, format);
					dumpTranslated(packName, source.getName() + "_" + layer + "_" + mode, result);
					List<String> problems = terrainLinkCheck(source.getName(), result, format, drawBuffers);
					expect(label, problems.isEmpty(), String.join("; ", problems));
					System.out.printf("  %-34s %s  writes %s, %s%n", label, problems.isEmpty() ? "OK  " : "FAIL", java.util.Arrays.toString(drawBuffers), IrisVulkanGlslTranslator.describe(result));
				} catch (RuntimeException e) {
					expect(label, false, e.toString());
					System.out.printf("  %-34s FAIL %s%n", label, e);
				}
			}
		}
	}

	private static void dumpTranslated(String packName, String name, IrisVulkanGlslTranslator.Result result) {
		try {
			Path dir = Path.of(System.getProperty("iris.vulkanCheck.dir"), "translated", packName);
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(name + ".vsh"), result.vertex());
			Files.writeString(dir.resolve(name + ".fsh"), result.fragment());
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/** PipelineBuilder's rules for a terrain pipeline: vertex inputs come from Sodium's buffer, plus Sodium's resources. */
	private List<String> terrainLinkCheck(String name, IrisVulkanGlslTranslator.Result result, VertexFormat format, int[] drawBuffers) {
		List<String> problems = new ArrayList<>();
		Reflection vertex = reflect(name + ".vsh", result.vertex(), 0);
		Reflection fragment = reflect(name + ".fsh", result.fragment(), 1);

		for (Var input : vertex.inputs) {
			VertexFormatElement element = format.getElements().stream().filter(e -> e.name().equals(input.name)).findFirst().orElse(null);
			if (element == null) {
				problems.add("vertex input " + input.name + " is not in the terrain vertex format");
				continue;
			}
			int expected = switch (element.format().componentType()) {
				case UINT_8, UINT_16, UINT_32 -> SPVC_BASETYPE_UINT32;
				case SINT_8, SINT_16, SINT_32 -> SPVC_BASETYPE_INT32;
				default -> SPVC_BASETYPE_FP32;
			};
			if (input.baseType != expected || input.vectorSize > element.format().componentCount()) {
				problems.add("vertex input " + input.name + " (base type " + input.baseType + ", " + input.vectorSize + " components) does not match " + element.format());
			}
		}

		Map<Integer, Var> outputs = new HashMap<>();
		vertex.outputs.forEach(output -> outputs.put(output.location, output));
		for (Var input : fragment.inputs) {
			Var output = outputs.get(input.location);
			if (output == null) {
				problems.add("fragment input " + input.name + " at location " + input.location + " has no vertex output");
			} else if (output.baseType != input.baseType || output.vectorSize != input.vectorSize || output.flat != input.flat) {
				problems.add("varying mismatch at location " + input.location + ": " + output + " vs " + input);
			}
		}

		Set<String> declared = new TreeSet<>(result.samplers());
		declared.addAll(result.texelBuffers());
		declared.addAll(result.uniformBlocks());
		if (!result.uniformFields().isEmpty()) {
			declared.add(IrisVulkanGlslTranslator.UNIFORM_BLOCK);
		}
		Set<String> reflected = new TreeSet<>(vertex.descriptors);
		reflected.addAll(fragment.descriptors);
		if (!declared.containsAll(reflected)) {
			problems.add("shader descriptors " + reflected + " are not all in the BindGroupLayout " + declared);
		}

		Set<Integer> fragmentOutputs = new TreeSet<>();
		fragment.outputs.forEach(output -> fragmentOutputs.add(output.location));
		if (!fragmentOutputs.equals(new TreeSet<>(result.fragmentOutputs()))) {
			problems.add("fragment outputs " + fragmentOutputs + " differ from " + result.fragmentOutputs());
		}
		if (fragmentOutputs.stream().anyMatch(location -> location >= drawBuffers.length)) {
			problems.add("fragment outputs " + fragmentOutputs + " exceed the draw buffers " + java.util.Arrays.toString(drawBuffers));
		}

		if (vertex.pushConstants + fragment.pushConstants > 0 && !result.pushConstants()) {
			problems.add("push constants are declared but the translator did not report them");
		}
		if (Math.max(vertex.pushConstantBytes, fragment.pushConstantBytes) > IrisVulkanTerrain.PUSH_CONSTANT_SIZE) {
			problems.add("push constant block is larger than Sodium's " + IrisVulkanTerrain.PUSH_CONSTANT_SIZE + " bytes");
		}

		for (IrisVulkanGlslTranslator.UniformField field : result.uniformFields()) {
			for (Reflection stage : List.of(vertex, fragment)) {
				Integer offset = stage.blockOffsets.get(field.name());
				if (offset != null && offset != field.offset()) {
					problems.add("std140 offset of " + field.name() + " is " + offset + " in SPIR-V but " + field.offset() + " in the writer");
				}
			}
		}

		return problems;
	}

	private static Map<String, String> declaredUniforms(Map<PatchShaderType, String> transformed) {
		Map<String, String> declared = new HashMap<>();
		for (PatchShaderType stage : List.of(PatchShaderType.VERTEX, PatchShaderType.FRAGMENT)) {
			Matcher matcher = UNIFORM.matcher(transformed.get(stage));
			while (matcher.find()) {
				if (!matcher.group(1).contains("sampler") && !matcher.group(1).startsWith("image")) {
					declared.put(matcher.group(2), matcher.group(1));
				}
			}
		}
		return declared;
	}

	/** RenderPearl's PipelineBuilder rules, plus the std140 offsets IrisVulkanUniforms writes. */
	private List<String> linkCheck(String name, IrisVulkanGlslTranslator.Result result) {
		List<String> problems = new ArrayList<>();
		Reflection vertex = reflect(name + ".vsh", result.vertex(), 0);
		Reflection fragment = reflect(name + ".fsh", result.fragment(), 1);

		for (Var input : vertex.inputs) {
			if (!IrisVulkanProgram.QUAD_FORMAT.contains(input.name) || input.baseType != SPVC_BASETYPE_FP32) {
				problems.add("vertex input " + input.name + " has no float element in the quad format");
			}
		}

		Map<Integer, Var> outputs = new HashMap<>();
		vertex.outputs.forEach(output -> outputs.put(output.location, output));
		for (Var input : fragment.inputs) {
			Var output = outputs.get(input.location);
			if (output == null) {
				problems.add("fragment input " + input.name + " at location " + input.location + " has no vertex output");
			} else if (output.baseType != input.baseType || output.vectorSize != input.vectorSize || output.flat != input.flat) {
				problems.add("varying mismatch at location " + input.location + ": " + output + " vs " + input);
			}
		}

		Set<String> declared = new TreeSet<>(result.samplers());
		if (!result.uniformFields().isEmpty()) {
			declared.add(IrisVulkanGlslTranslator.UNIFORM_BLOCK);
		}
		Set<String> reflected = new TreeSet<>(vertex.descriptors);
		reflected.addAll(fragment.descriptors);
		if (!declared.equals(reflected)) {
			problems.add("shader descriptors " + reflected + " differ from the BindGroupLayout " + declared);
		}

		Set<Integer> fragmentOutputs = new TreeSet<>();
		fragment.outputs.forEach(output -> fragmentOutputs.add(output.location));
		if (!fragmentOutputs.equals(new TreeSet<>(result.fragmentOutputs()))) {
			problems.add("fragment outputs " + fragmentOutputs + " differ from " + result.fragmentOutputs());
		}

		if (vertex.pushConstants + fragment.pushConstants > 0) {
			problems.add("push constants are declared but the pipeline has none");
		}

		for (IrisVulkanGlslTranslator.UniformField field : result.uniformFields()) {
			if (!vertex.blockOffsets.containsKey(field.name()) && !fragment.blockOffsets.containsKey(field.name())) {
				problems.add("uniform block member " + field.name() + " was not found in the reflected SPIR-V");
			}
			for (Reflection stage : List.of(vertex, fragment)) {
				Integer offset = stage.blockOffsets.get(field.name());
				if (offset != null && offset != field.offset()) {
					problems.add("std140 offset of " + field.name() + " is " + offset + " in SPIR-V but " + field.offset() + " in the writer");
				}
			}
		}

		return problems;
	}

	private Reflection reflect(String fileName, String source, int kind) {
		long options = shaderc_compile_options_initialize();
		// Same options as RenderPearl's GlslCompiler#createBaseShaderOptions on a zero-to-one depth device.
		shaderc_compile_options_set_target_env(options, 0, 4202496);
		shaderc_compile_options_set_auto_bind_uniforms(options, true);
		shaderc_compile_options_set_preserve_bindings(options, false);
		shaderc_compile_options_set_generate_debug_info(options);
		shaderc_compile_options_set_optimization_level(options, 0);
		shaderc_compile_options_add_macro_definition(options, "RENDERPEARL_DEPTH_IS_ZERO_TO_ONE", "");
		ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, false);
		ByteBuffer nameBuffer = MemoryUtil.memUTF8(fileName);
		ByteBuffer entryBuffer = MemoryUtil.memUTF8("main");
		long result = shaderc_compile_into_spv(compiler, sourceBuffer, kind, nameBuffer, entryBuffer, options);
		try {
			if (shaderc_result_get_compilation_status(result) != 0) {
				throw new IllegalStateException("shaderc rejected " + fileName + ": " + shaderc_result_get_error_message(result));
			}

			return reflectSpirv(shaderc_result_get_bytes(result));
		} finally {
			shaderc_result_release(result);
			shaderc_compile_options_release(options);
			MemoryUtil.memFree(sourceBuffer);
			MemoryUtil.memFree(nameBuffer);
			MemoryUtil.memFree(entryBuffer);
		}
	}

	private static Reflection reflectSpirv(ByteBuffer spirv) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer pointer = stack.callocPointer(1);
			check(spvc_context_create(pointer));
			long context = pointer.get(0);
			try {
				IntBuffer words = spirv.asIntBuffer();
				check(spvc_context_parse_spirv(context, words, words.remaining(), pointer));
				check(spvc_context_create_compiler(context, 0, pointer.get(0), 0, pointer));
				long spvc = pointer.get(0);
				check(spvc_compiler_create_shader_resources(spvc, pointer));
				long resources = pointer.get(0);

				Set<String> descriptors = new TreeSet<>();
				// Same descriptor resource types as RenderPearl's SpvUtil.DESCRIPTOR_TYPES.
				for (int type : new int[]{1, 2, 6, 7, 10, 11}) {
					for (SpvcReflectedResource resource : list(resources, type)) {
						descriptors.add(resource.nameString());
					}
				}

				Map<String, Integer> offsets = new TreeMap<>();
				for (SpvcReflectedResource resource : list(resources, SPVC_RESOURCE_TYPE_UNIFORM_BUFFER)) {
					if (!resource.nameString().equals(IrisVulkanGlslTranslator.UNIFORM_BLOCK)) {
						continue;
					}
					long type = spvc_compiler_get_type_handle(spvc, resource.base_type_id());
					IntBuffer offset = stack.callocInt(1);
					for (int member = 0; member < spvc_type_get_num_member_types(type); member++) {
						check(spvc_compiler_type_struct_member_offset(spvc, type, member, offset));
						offsets.put(spvc_compiler_get_member_name(spvc, resource.base_type_id(), member), offset.get(0));
					}
				}

				SpvcReflectedResource.Buffer pushConstants = list(resources, SPVC_RESOURCE_TYPE_PUSH_CONSTANT);
				int pushConstantBytes = 0;
				if (pushConstants.remaining() > 0) {
					PointerBuffer size = stack.callocPointer(1);
					check(spvc_compiler_get_declared_struct_size(spvc, spvc_compiler_get_type_handle(spvc, pushConstants.get(0).base_type_id()), size));
					pushConstantBytes = (int) size.get(0);
				}

				return new Reflection(variables(spvc, resources, SPVC_RESOURCE_TYPE_STAGE_INPUT), variables(spvc, resources, SPVC_RESOURCE_TYPE_STAGE_OUTPUT),
					descriptors, offsets, pushConstants.remaining(), pushConstantBytes);
			} finally {
				spvc_context_destroy(context);
			}
		}
	}

	private static List<Var> variables(long spvc, long resources, int type) {
		List<Var> variables = new ArrayList<>();
		for (SpvcReflectedResource resource : list(resources, type)) {
			long typeHandle = spvc_compiler_get_type_handle(spvc, resource.type_id());
			variables.add(new Var(resource.nameString(), spvc_compiler_get_decoration(spvc, resource.id(), 30),
				spvc_type_get_basetype(typeHandle), spvc_type_get_vector_size(typeHandle), spvc_compiler_has_decoration(spvc, resource.id(), 14)));
		}
		return variables;
	}

	private static SpvcReflectedResource.Buffer list(long resources, int type) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer list = stack.callocPointer(1);
			PointerBuffer count = stack.callocPointer(1);
			check(spvc_resources_get_resource_list_for_type(resources, type, list, count));
			return SpvcReflectedResource.create(list.get(0), (int) count.get(0));
		}
	}

	private static void check(int result) {
		if (result != 0) {
			throw new IllegalStateException("SPIRV-Cross error " + result);
		}
	}

	private static final String QUAD_VERTEX = """
		#version 330 core
		in vec3 Position;
		in vec2 UV0;
		out vec2 texcoord;
		void main() { texcoord = UV0; gl_Position = vec4(Position * 2.0 - 1.0, 1.0); }
		""";

	/** Unsupported constructs must be rejected by name, not turned into a broken pipeline. */
	private void negativeTests() {
		System.out.println("== negative tests");
		rejects("sampler3D", QUAD_VERTEX, fragment("uniform sampler3D volume;", "texture(volume, vec3(texcoord, 0.5))"), "sampler3D");
		rejects("sampler1D", QUAD_VERTEX, fragment("uniform sampler1D line;", "texture(line, texcoord.x)"), "sampler1D");
		rejects("image2D", QUAD_VERTEX, fragment("layout(rgba8) uniform image2D img;", "imageLoad(img, ivec2(0))"), "image2D");
		rejects("sampler array", QUAD_VERTEX, fragment("uniform sampler2D many[2];", "texture(many[1], texcoord)"), "sampler arrays");
		rejects("textureGrad on shadow sampler", QUAD_VERTEX,
			fragment("uniform sampler2DShadow shadowtex0;", "vec4(textureGrad(shadowtex0, vec3(texcoord, 0.5), vec2(0.0), vec2(0.0)))"), "textureGrad");
		rejects("unwritten varying", QUAD_VERTEX, fragment("in vec3 neverWritten;", "vec4(neverWritten, 1.0)"), "neverWritten");
		rejects("vertex input outside quad format", """
			#version 330 core
			in vec3 Position;
			in vec4 Color;
			out vec2 texcoord;
			void main() { texcoord = Color.xy; gl_Position = vec4(Position, 1.0); }
			""", fragment("", "vec4(1.0)"), "Color");

		Map<PatchShaderType, String> withGeometry = new HashMap<>(Map.of(PatchShaderType.VERTEX, QUAD_VERTEX, PatchShaderType.FRAGMENT, fragment("", "vec4(1.0)"),
			PatchShaderType.GEOMETRY, "#version 330 core\nlayout(triangles) in;\nlayout(triangle_strip, max_vertices = 3) out;\nvoid main() {}\n"));
		rejects("geometry stage", withGeometry, "geometry");

		// Accepted by the translator (the qualifiers are copied verbatim) but rejected by PipelineBuilder's rules.
		String flatMismatch = "#version 330 core\nin vec3 Position;\nout vec2 texcoord;\nvoid main() { texcoord = Position.xy; gl_Position = vec4(Position, 1.0); }\n";
		String flatFragment = "#version 330 core\nflat in vec2 texcoord;\nlayout(location = 0) out vec4 iris_FragData0;\nvoid main() { iris_FragData0 = vec4(texcoord, 0.0, 1.0); }\n";
		IrisVulkanGlslTranslator.Result result = IrisVulkanProgram.prepare("flatMismatch", Map.of(PatchShaderType.VERTEX, flatMismatch, PatchShaderType.FRAGMENT, flatFragment), Map.of());
		List<String> problems = linkCheck("flatMismatch", result);
		expect("negative: flat mismatch is caught by the link check", problems.stream().anyMatch(p -> p.contains("varying mismatch")), String.valueOf(problems));
		System.out.printf("  %-34s %s%n", "flat interpolation mismatch", problems.isEmpty() ? "NOT CAUGHT" : "rejected: " + problems.getFirst());
	}

	private static String fragment(String declarations, String color) {
		return "#version 330 core\nin vec2 texcoord;\n" + declarations + "\nlayout(location = 0) out vec4 iris_FragData0;\nvoid main() { iris_FragData0 = " + color + "; }\n";
	}

	private void rejects(String label, String vertex, String fragment, String expectedInMessage) {
		rejects(label, Map.of(PatchShaderType.VERTEX, vertex, PatchShaderType.FRAGMENT, fragment), expectedInMessage);
	}

	private void rejects(String label, Map<PatchShaderType, String> sources, String expectedInMessage) {
		try {
			IrisVulkanProgram.prepare(label.replace(' ', '_'), sources, Map.of());
			expect("negative: " + label, false, "was accepted");
			System.out.printf("  %-34s NOT REJECTED%n", label);
		} catch (IrisVulkanGlslTranslator.TranslationException e) {
			expect("negative: " + label, e.getMessage().contains(expectedInMessage), "wrong message: " + e.getMessage());
			System.out.printf("  %-34s rejected: %s%n", label, e.getMessage());
		}
	}

	private void expect(String label, boolean condition) {
		expect(label, condition, "");
	}

	private void expect(String label, boolean condition, String detail) {
		checks++;
		if (!condition) {
			failures.add(label + (detail.isEmpty() ? "" : ": " + detail));
		}
	}
}
