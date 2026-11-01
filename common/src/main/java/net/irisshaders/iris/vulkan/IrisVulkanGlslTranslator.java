package net.irisshaders.iris.vulkan;

import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.InterfaceBlockDeclaration;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.Expression;
import io.github.douira.glsl_transformer.ast.node.expression.ReferenceExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.FunctionCallExpression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.LayoutQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.TypeQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.TypeQualifierPart;
import io.github.douira.glsl_transformer.ast.print.ASTPrinter;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.SingleASTTransformer;
import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates the OpenGL-flavoured GLSL that Iris's {@code TransformPatcher} produces for a vertex/fragment pair
 * into GLSL that RenderPearl's shaderc front end accepts for Vulkan 1.2, and reports the resulting interface so
 * the caller can build a matching {@code BindGroupLayout}, vertex format and uniform buffer.
 *
 * <ul>
 *   <li>Non-opaque uniforms Iris supplies (by name and GLSL type) move into one {@code layout(std140) uniform}
 *       block with identical layout in both stages. Uniforms Iris does not supply become plain globals holding
 *       their declared initializer, or zero, which is what OpenGL gives an unset uniform.</li>
 *   <li>Unused uniforms, samplers, vertex inputs and fragment inputs are removed, so the reflected interface only
 *       contains resources that must actually be bound.</li>
 *   <li>Vertex inputs, varyings and fragment outputs get explicit locations; varyings are matched by name.</li>
 *   <li>{@code sampler2DShadow} becomes {@code sampler2D} with an explicit LEQUAL comparison, since RenderPearl
 *       samplers have no compare mode.</li>
 *   <li>Uniform blocks already in the source (Sodium's {@code u_Globals}) are kept and reported; the block named by
 *       {@code pushConstantBlock} becomes the stage's {@code push_constant} block. Buffer samplers (Sodium's
 *       {@code u_SectionTimeInfo}) are kept as texel buffers.</li>
 * </ul>
 *
 * <p>Anything RenderPearl cannot express (geometry/tessellation stages, 1D/3D/cube/array samplers, images)
 * fails with a {@link TranslationException} naming the construct instead of producing a broken pipeline.</p>
 */
public final class IrisVulkanGlslTranslator {
	public static final String UNIFORM_BLOCK = "IrisUniforms";

	private static final Pattern VERSION = Pattern.compile("#version\\s+(\\d+)[^\\n]*\\n");
	private static final Pattern ARRAY = Pattern.compile("\\[\\s*(\\d+)\\s*]");
	private static final Set<String> SHADOW_FUNCTIONS = Set.of("texture", "textureLod");
	private static final String SHADOW_HELPERS = """
		float iris_vkShadow_texture(sampler2D s, vec3 c) { return step(c.z, texture(s, c.xy).r); }
		float iris_vkShadow_texture(sampler2D s, vec3 c, float bias) { return step(c.z, texture(s, c.xy, bias).r); }
		float iris_vkShadow_textureLod(sampler2D s, vec3 c, float lod) { return step(c.z, textureLod(s, c.xy, lod).r); }
		""";

	public record UniformField(String name, String glslType, int offset) {
	}

	public record VertexInput(String name, String glslType, int location) {
	}

	/**
	 * @param uniformFields std140 members of {@link #UNIFORM_BLOCK} with byte offsets; empty when no block is needed
	 * @param uniformBlockSize size in bytes of the block (0 when absent)
	 * @param samplers combined image samplers referenced by either stage
	 * @param fragmentOutputs locations written by the fragment shader, ascending
	 * @param texelBuffers buffer samplers referenced by either stage (bound as {@code TEXEL_BUFFER})
	 * @param uniformBlocks uniform blocks the source declared itself, which the caller binds by name
	 * @param pushConstants whether either stage declares the push constant block
	 */
	public record Result(
		String vertex,
		String fragment,
		List<UniformField> uniformFields,
		int uniformBlockSize,
		List<String> samplers,
		List<VertexInput> vertexInputs,
		List<Integer> fragmentOutputs,
		List<String> texelBuffers,
		List<String> uniformBlocks,
		boolean pushConstants
	) {
	}

	public static final class TranslationException extends RuntimeException {
		public TranslationException(String programName, String message) {
			super("Iris/Vulkan cannot translate '" + programName + "': " + message);
		}
	}

	private IrisVulkanGlslTranslator() {
	}

	/**
	 * @param suppliedUniforms GLSL type (e.g. {@code float}, {@code ivec2}, {@code mat4}) of every non-opaque
	 *                         uniform the caller will write into the uniform block, keyed by name
	 */
	public static Result translate(String programName, String vertex, String fragment, Map<String, String> suppliedUniforms) {
		return translate(programName, vertex, fragment, suppliedUniforms, null);
	}

	/**
	 * @param pushConstantBlock name of a {@code layout(std140) uniform} block to turn into the push constant block,
	 *                          or null when the program has none
	 */
	public static Result translate(String programName, String vertex, String fragment, Map<String, String> suppliedUniforms, String pushConstantBlock) {
		if (vertex == null || fragment == null) {
			throw new TranslationException(programName, "both a vertex and a fragment shader are required");
		}

		Stage vs = new Stage(programName, vertex, true, pushConstantBlock);
		Stage fs = new Stage(programName, fragment, false, pushConstantBlock);

		// Uniform block: every supplied, used, matching-type uniform from either stage, in a stable order.
		Map<String, String> blockTypes = new TreeMap<>();
		for (Stage stage : List.of(vs, fs)) {
			for (Decl decl : stage.uniforms) {
				if (!stage.isUsed(decl.name)) {
					continue;
				}

				String supplied = suppliedUniforms.get(decl.name);
				if (supplied != null && supplied.equals(decl.type) && decl.arraySuffix.isEmpty()) {
					String previous = blockTypes.put(decl.name, decl.type);
					if (previous != null && !previous.equals(decl.type)) {
						throw new TranslationException(programName, "uniform " + decl.name + " is declared as " + previous + " and " + decl.type);
					}
				}
			}
		}

		List<UniformField> fields = new ArrayList<>();
		int offset = 0;
		for (Map.Entry<String, String> entry : blockTypes.entrySet()) {
			Std140 layout = Std140.of(programName, entry.getKey(), entry.getValue());
			offset = align(offset, layout.alignment);
			fields.add(new UniformField(entry.getKey(), entry.getValue(), offset));
			offset += layout.size;
		}
		int blockSize = align(offset, 16);

		// Varyings: vertex outputs define locations, fragment inputs reuse them by name.
		Map<String, Integer> varyingLocations = new LinkedHashMap<>();
		int nextLocation = 0;
		for (Decl decl : vs.outputs) {
			varyingLocations.put(decl.name, nextLocation);
			nextLocation += decl.locationSlots(programName);
		}

		Set<String> samplers = new LinkedHashSet<>();
		Set<String> texelBuffers = new LinkedHashSet<>();
		List<VertexInput> vertexInputs = new ArrayList<>();
		List<Integer> fragmentOutputs = new ArrayList<>();

		vs.rewrite(blockTypes, fields, samplers, texelBuffers, varyingLocations, vertexInputs, null);
		fs.rewrite(blockTypes, fields, samplers, texelBuffers, varyingLocations, null, fragmentOutputs);

		Set<String> uniformBlocks = new LinkedHashSet<>(vs.uniformBlocks);
		uniformBlocks.addAll(fs.uniformBlocks);
		boolean pushConstants = uniformBlocks.remove(pushConstantBlock);
		if (uniformBlocks.contains(UNIFORM_BLOCK)) {
			throw new TranslationException(programName, "the source already declares a uniform block named " + UNIFORM_BLOCK);
		}

		fragmentOutputs.sort(Integer::compare);
		return new Result(
			vs.print(), fs.print(), List.copyOf(fields), blockSize, List.copyOf(samplers), List.copyOf(vertexInputs), List.copyOf(fragmentOutputs),
			List.copyOf(texelBuffers), List.copyOf(uniformBlocks), pushConstants
		);
	}

	private static int align(int value, int alignment) {
		return (value + alignment - 1) / alignment * alignment;
	}

	/** One member of a top-level declaration, with the declaration it came from. */
	private record Decl(
		DeclarationExternalDeclaration external,
		TypeAndInitDeclaration declaration,
		String name,
		String type,
		String arraySuffix,
		String initializer,
		String qualifiersWithoutStorage,
		boolean hasLayout
	) {
		int locationSlots(String programName) {
			int columns = switch (type) {
				case "mat2", "mat2x2", "mat2x3", "mat2x4", "dvec3", "dvec4" -> 2;
				case "mat3", "mat3x2", "mat3x3", "mat3x4" -> 3;
				case "mat4", "mat4x2", "mat4x3", "mat4x4" -> 4;
				default -> 1;
			};

			if (arraySuffix.isEmpty()) {
				return columns;
			}

			Matcher matcher = ARRAY.matcher(arraySuffix);
			if (!matcher.matches()) {
				throw new TranslationException(programName, "varying " + name + " has a non-constant array size " + arraySuffix);
			}

			return columns * Integer.parseInt(matcher.group(1));
		}
	}

	private static final class Stage {
		private final String programName;
		private final boolean vertex;
		private final String pushConstantBlock;
		private final int version;
		private final SingleASTTransformer<JobParameters> parser = new SingleASTTransformer<>();
		private final TranslationUnit tree;
		private final Root root;
		private final List<Decl> uniforms = new ArrayList<>();
		private final List<Decl> opaqueUniforms = new ArrayList<>();
		private final List<Decl> inputs = new ArrayList<>();
		private final List<Decl> outputs = new ArrayList<>();
		private final Set<String> uniformBlocks = new LinkedHashSet<>();

		Stage(String programName, String source, boolean vertex, String pushConstantBlock) {
			this.programName = programName;
			this.vertex = vertex;
			this.pushConstantBlock = pushConstantBlock;
			Matcher matcher = VERSION.matcher(source);
			if (!matcher.find()) {
				throw new TranslationException(programName, "no #version directive");
			}

			this.version = Integer.parseInt(matcher.group(1));
			parser.setPrintType(PrintType.SIMPLE);
			parser.getLexer().version = Version.fromNumber(version);
			this.tree = parser.parseTranslationUnit(RootSupplier.PREFIX_UNORDERED_ED_EXACT, source);
			this.root = tree.getRoot();
			collect();
		}

		private void collect() {
			for (ExternalDeclaration external : tree.getChildren()) {
				if (external instanceof DeclarationExternalDeclaration declarationExternal
					&& declarationExternal.getDeclaration() instanceof InterfaceBlockDeclaration block
					&& isUniformBlock(block)) {
					uniformBlocks.add(block.getBlockName().getName());
					continue;
				}

				if (!(external instanceof DeclarationExternalDeclaration declarationExternal)
					|| !(declarationExternal.getDeclaration() instanceof TypeAndInitDeclaration declaration)) {
					continue;
				}

				TypeQualifier qualifier = declaration.getType().getTypeQualifier();
				if (qualifier == null) {
					continue;
				}

				StorageType storage = null;
				boolean hasLayout = false;
				StringBuilder otherQualifiers = new StringBuilder();
				for (TypeQualifierPart part : qualifier.getParts()) {
					if (part instanceof StorageQualifier storageQualifier
						&& (storageQualifier.storageType == StorageType.UNIFORM
						|| storageQualifier.storageType == StorageType.IN
						|| storageQualifier.storageType == StorageType.OUT)) {
						storage = storageQualifier.storageType;
					} else {
						hasLayout |= part instanceof LayoutQualifier;
						otherQualifiers.append(ASTPrinter.printSimple(part).trim()).append(' ');
					}
				}

				if (storage == null) {
					continue;
				}

				String type = ASTPrinter.printSimple(declaration.getType().getTypeSpecifier()).trim();
				for (DeclarationMember member : declaration.getMembers()) {
					String arraySuffix = member.getArraySpecifier() == null ? "" : ASTPrinter.printSimple(member.getArraySpecifier()).trim();
					String initializer = member.getInitializer() == null ? null : ASTPrinter.printSimple(member.getInitializer()).trim();
					Decl decl = new Decl(declarationExternal, declaration, member.getName().getName(), type, arraySuffix, initializer, otherQualifiers.toString(), hasLayout);
					switch (storage) {
						case UNIFORM -> (isOpaque(type) ? opaqueUniforms : uniforms).add(decl);
						case IN -> inputs.add(decl);
						case OUT -> outputs.add(decl);
						default -> {
						}
					}
				}
			}
		}

		private static boolean isUniformBlock(InterfaceBlockDeclaration block) {
			for (TypeQualifierPart part : block.getTypeQualifier().getParts()) {
				if (part instanceof StorageQualifier storage && storage.storageType == StorageType.UNIFORM) {
					return true;
				}
			}

			return false;
		}

		boolean isUsed(String name) {
			// The declaring identifier is itself indexed, so any further occurrence is a use.
			Set<Identifier> occurrences = root.identifierIndex.get(name);
			return occurrences != null && occurrences.size() > 1;
		}

		void rewrite(
			Map<String, String> blockTypes,
			List<UniformField> fields,
			Set<String> samplers,
			Set<String> texelBuffers,
			Map<String, Integer> varyingLocations,
			List<VertexInput> vertexInputs,
			List<Integer> fragmentOutputs
		) {
			root.indexBuildSession(() -> {
				Set<DeclarationExternalDeclaration> handled = new LinkedHashSet<>();
				List<String> injected = new ArrayList<>();

				rewriteUniforms(blockTypes, handled);
				Set<String> shadowSamplers = rewriteSamplers(samplers, texelBuffers, handled, injected);
				rewriteInterface(varyingLocations, vertexInputs, fragmentOutputs, handled, injected);

				if (!fields.isEmpty()) {
					StringBuilder block = new StringBuilder("layout(std140) uniform ").append(UNIFORM_BLOCK).append(" {\n");
					for (UniformField field : fields) {
						block.append(field.glslType()).append(' ').append(field.name()).append(";\n");
					}
					injected.addFirst(block.append("};").toString());
				}

				for (int i = injected.size() - 1; i >= 0; i--) {
					tree.parseAndInjectNode(parser, ASTInjectionPoint.BEFORE_DECLARATIONS, injected.get(i));
				}

				if (!shadowSamplers.isEmpty()) {
					rewriteShadowCalls(shadowSamplers);
					for (String helper : SHADOW_HELPERS.strip().split("\n")) {
						tree.parseAndInjectNode(parser, ASTInjectionPoint.BEFORE_FUNCTIONS, helper);
					}
				}

				if (vertex) {
					root.rename("gl_VertexID", "gl_VertexIndex");
					root.rename("gl_InstanceID", "gl_InstanceIndex");
				}
			});
		}

		private void rewriteUniforms(Map<String, String> blockTypes, Set<DeclarationExternalDeclaration> handled) {
			// Group members by declaration so multi-member declarations are rewritten once.
			Map<DeclarationExternalDeclaration, List<Decl>> byDeclaration = new LinkedHashMap<>();
			for (Decl decl : uniforms) {
				byDeclaration.computeIfAbsent(decl.external, k -> new ArrayList<>()).add(decl);
			}

			for (Map.Entry<DeclarationExternalDeclaration, List<Decl>> entry : byDeclaration.entrySet()) {
				List<String> members = new ArrayList<>();
				String type = entry.getValue().getFirst().type;
				for (Decl decl : entry.getValue()) {
					if (blockTypes.containsKey(decl.name) || !isUsed(decl.name)) {
						continue;
					}

					// Not supplied: behave like an unset OpenGL uniform.
					String value = decl.initializer != null ? decl.initializer : zeroValue(decl.type, decl.arraySuffix);
					members.add(decl.name + decl.arraySuffix + (value == null ? "" : " = " + value));
				}

				handled.add(entry.getKey());
				if (members.isEmpty()) {
					entry.getKey().detachAndDelete();
				} else {
					entry.getKey().replaceByAndDelete(parser.parseExternalDeclaration(root, type + " " + String.join(", ", members) + ";"));
				}
			}
		}

		private Set<String> rewriteSamplers(Set<String> samplers, Set<String> texelBuffers, Set<DeclarationExternalDeclaration> handled, List<String> injected) {
			Set<String> shadowSamplers = new LinkedHashSet<>();
			for (Decl decl : opaqueUniforms) {
				handled.add(decl.external);
				if (!isUsed(decl.name)) {
					continue;
				}

				String type = decl.type;
				if (!decl.arraySuffix.isEmpty()) {
					throw new TranslationException(programName, "sampler arrays are not supported (" + decl.name + ")");
				}

				if (type.equals("samplerBuffer") || type.equals("isamplerBuffer") || type.equals("usamplerBuffer")) {
					texelBuffers.add(decl.name);
					injected.add("uniform " + type + " " + decl.name + ";");
					continue;
				}

				if (type.equals("sampler2DShadow")) {
					shadowSamplers.add(decl.name);
					type = "sampler2D";
				} else if (!type.equals("sampler2D") && !type.equals("isampler2D") && !type.equals("usampler2D")) {
					throw new TranslationException(programName, decl.name + " uses " + type + "; RenderPearl only binds 2D combined image samplers");
				}

				samplers.add(decl.name);
				injected.add("uniform " + type + " " + decl.name + ";");
			}

			for (Decl decl : opaqueUniforms) {
				if (decl.external.getParent() != null) {
					decl.external.detachAndDelete();
				}
			}

			return shadowSamplers;
		}

		private void rewriteShadowCalls(Set<String> shadowSamplers) {
			for (FunctionCallExpression call : new ArrayList<>(root.nodeIndex.get(FunctionCallExpression.class))) {
				Identifier function = call.getFunctionName();
				List<Expression> parameters = call.getParameters();
				if (function == null || parameters.isEmpty()
					|| !(parameters.getFirst() instanceof ReferenceExpression reference)
					|| !shadowSamplers.contains(reference.getIdentifier().getName())) {
					continue;
				}

				if (!SHADOW_FUNCTIONS.contains(function.getName())) {
					throw new TranslationException(programName, function.getName() + "() on shadow sampler " + reference.getIdentifier().getName() + " is not supported");
				}

				function.setName("iris_vkShadow_" + function.getName());
			}
		}

		private void rewriteInterface(
			Map<String, Integer> varyingLocations,
			List<VertexInput> vertexInputs,
			List<Integer> fragmentOutputs,
			Set<DeclarationExternalDeclaration> handled,
			List<String> injected
		) {
			if (vertex) {
				int location = 0;
				for (Decl decl : inputs) {
					if (decl.hasLayout) {
						throw new TranslationException(programName, "vertex input " + decl.name + " already has a layout qualifier");
					}

					handled.add(decl.external);
					if (!isUsed(decl.name)) {
						continue;
					}

					vertexInputs.add(new VertexInput(decl.name, decl.type, location));
					injected.add("layout(location = " + location + ") " + decl.qualifiersWithoutStorage + "in " + decl.type + " " + decl.name + decl.arraySuffix + ";");
					location += decl.locationSlots(programName);
				}

				for (Decl decl : outputs) {
					handled.add(decl.external);
					injected.add(varying(decl, "out", varyingLocations.get(decl.name)));
				}
			} else {
				for (Decl decl : inputs) {
					handled.add(decl.external);
					if (!isUsed(decl.name)) {
						continue;
					}

					Integer location = varyingLocations.get(decl.name);
					if (location == null) {
						throw new TranslationException(programName, "fragment input " + decl.name + " is not written by the vertex shader");
					}

					injected.add(varying(decl, "in", location));
				}

				for (Decl decl : outputs) {
					if (decl.hasLayout) {
						Matcher matcher = Pattern.compile("location\\s*=\\s*(\\d+)").matcher(decl.qualifiersWithoutStorage);
						if (!matcher.find()) {
							throw new TranslationException(programName, "fragment output " + decl.name + " has a layout without a location");
						}
						fragmentOutputs.add(Integer.parseInt(matcher.group(1)));
						continue;
					}

					if (outputs.size() != 1) {
						throw new TranslationException(programName, "fragment output " + decl.name + " has no location and is not the only output");
					}

					handled.add(decl.external);
					fragmentOutputs.add(0);
					injected.add("layout(location = 0) " + decl.qualifiersWithoutStorage + "out " + decl.type + " " + decl.name + decl.arraySuffix + ";");
				}
			}

			for (DeclarationExternalDeclaration external : handled) {
				if (external.getParent() != null && isInterface(external)) {
					external.detachAndDelete();
				}
			}
		}

		private boolean isInterface(DeclarationExternalDeclaration external) {
			for (Decl decl : inputs) {
				if (decl.external == external) {
					return true;
				}
			}

			for (Decl decl : outputs) {
				if (decl.external == external) {
					return true;
				}
			}

			return false;
		}

		private String varying(Decl decl, String direction, Integer location) {
			if (decl.hasLayout) {
				throw new TranslationException(programName, "varying " + decl.name + " already has a layout qualifier");
			}

			return "layout(location = " + location + ") " + decl.qualifiersWithoutStorage + direction + " " + decl.type + " " + decl.name + decl.arraySuffix + ";";
		}

		String print() {
			String printed = ASTPrinter.print(PrintType.SIMPLE, tree);
			if (pushConstantBlock != null && uniformBlocks.contains(pushConstantBlock)) {
				Matcher block = Pattern.compile("layout\\s*\\(\\s*std140\\s*\\)\\s*uniform\\s+" + Pattern.quote(pushConstantBlock) + "\\b").matcher(printed);
				if (!block.find()) {
					throw new TranslationException(programName, "push constant block " + pushConstantBlock + " is not declared as layout(std140) uniform");
				}
				printed = printed.substring(0, block.start()) + "layout(push_constant) uniform " + pushConstantBlock + printed.substring(block.end());
			}

			if (version >= 410) {
				return printed;
			}

			// Explicit varying locations need separate_shader_objects below 4.10, as vanilla 26.3's shaders do.
			Matcher matcher = VERSION.matcher(printed);
			if (!matcher.find()) {
				throw new TranslationException(programName, "lost #version directive while printing");
			}

			return printed.substring(0, matcher.end()) + "#extension GL_ARB_separate_shader_objects : require\n" + printed.substring(matcher.end());
		}
	}

	private static boolean isOpaque(String type) {
		return type.contains("sampler") || type.startsWith("image") || type.startsWith("iimage") || type.startsWith("uimage")
			|| type.startsWith("texture") || type.startsWith("atomic_uint");
	}

	private static String zeroValue(String type, String arraySuffix) {
		if (!arraySuffix.isEmpty()) {
			return null;
		}

		return switch (type) {
			case "float" -> "0.0";
			case "int" -> "0";
			case "uint" -> "0u";
			case "bool" -> "false";
			case "vec2", "vec3", "vec4", "mat2", "mat3", "mat4" -> type + "(0.0)";
			case "ivec2", "ivec3", "ivec4" -> type + "(0)";
			case "uvec2", "uvec3", "uvec4" -> type + "(0u)";
			case "bvec2", "bvec3", "bvec4" -> type + "(false)";
			default -> null;
		};
	}

	private record Std140(int alignment, int size) {
		static Std140 of(String programName, String name, String type) {
			return switch (type) {
				case "float", "int", "uint", "bool" -> new Std140(4, 4);
				case "vec2", "ivec2", "uvec2", "bvec2" -> new Std140(8, 8);
				case "vec3", "ivec3", "uvec3", "bvec3" -> new Std140(16, 12);
				case "vec4", "ivec4", "uvec4", "bvec4" -> new Std140(16, 16);
				case "mat2" -> new Std140(16, 32);
				case "mat3" -> new Std140(16, 48);
				case "mat4" -> new Std140(16, 64);
				default -> throw new TranslationException(programName, "uniform " + name + " has type " + type + " which has no std140 mapping here");
			};
		}
	}

	static String describe(Result result) {
		String extra = result.texelBuffers().isEmpty() && result.uniformBlocks().isEmpty() && !result.pushConstants() ? ""
			: String.format(Locale.ROOT, "; texel buffers %s, own blocks %s%s", result.texelBuffers(), result.uniformBlocks(), result.pushConstants() ? ", push constants" : "");
		return String.format(Locale.ROOT, "%d block uniforms (%d bytes), %d samplers, %d vertex inputs, outputs %s%s",
			result.uniformFields().size(), result.uniformBlockSize(), result.samplers().size(), result.vertexInputs().size(), result.fragmentOutputs(), extra);
	}
}
