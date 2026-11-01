package net.irisshaders.iris.vulkan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.item.IrisItemLightProvider;
import net.irisshaders.iris.mixin.IrisMixinPlugin;
import net.minecraft.world.item.AirItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Guards against shared Iris code depending on a mixin that is not applied on Vulkan.
 *
 * <p>On Vulkan IrisMixinPlugin applies only part of Iris's mixins. Code that also runs on Vulkan (uniform
 * suppliers, pack loading and transformation, the vulkan package, and every mixin applied on Vulkan) must not cast
 * to an interface that only an OpenGL-mode mixin injects: that is how {@code AirItem cannot be cast to
 * IrisItemLightProvider} reached frame execution. The scan reads the compiled classes and asks the real
 * {@link IrisMixinPlugin#shouldApplyMixin} for both backends.</p>
 *
 * <p>It also checks the behaviour those casts rely on: {@link IrisItemLightProvider}'s default methods, which every
 * item gets through MixinItem, for an empty hand ({@code AirItem}), a light-emitting block item and a plain item.</p>
 */
final class VulkanMixinCoverageCheck {
	private static final List<String> PLUGIN_CONFIGS = List.of(
		"mixins.iris.json", "mixins.iris.vertexformat.json", "mixins.iris.bettermipmaps.json", "mixins.iris.compat.sodium.json"
	);
	/** Packages whose code runs on Vulkan. */
	private static final List<String> VULKAN_PACKAGES = List.of(
		"net/irisshaders/iris/uniforms/", "net/irisshaders/iris/vulkan/", "net/irisshaders/iris/shaderpack/", "net/irisshaders/iris/pipeline/transform/"
	);
	private static final List<String> VULKAN_CLASSES = List.of(
		"net/irisshaders/iris/Iris", "net/irisshaders/iris/IrisVKOnly", "net/irisshaders/iris/gl/shader/StandardMacros", "net/irisshaders/iris/features/FeatureFlags"
	);
	/** Mixins applied on Vulkan for their interface must not reach into the OpenGL backend. */
	private static final List<String> OPENGL_REFERENCES = List.of("com/mojang/renderpearl/backend/opengl/", "org/lwjgl/opengl/");

	private final Consumer<String> failure;
	private int checks;

	private VulkanMixinCoverageCheck(Consumer<String> failure) {
		this.failure = failure;
	}

	static int run(Consumer<String> failure) throws Exception {
		VulkanMixinCoverageCheck check = new VulkanMixinCoverageCheck(failure);
		check.checkMixinCoverage();
		check.checkItemLightDefaults();
		return check.checks;
	}

	private record MixinInfo(String name, String target, ClassNode node) {
	}

	private void checkMixinCoverage() throws Exception {
		System.out.println("== Mixin coverage on Vulkan");
		List<MixinInfo> mixins = loadMixins();
		// Interface internal name -> the mixin that makes game objects implement it (accessors are their own interface).
		Map<String, MixinInfo> providers = new HashMap<>();
		for (MixinInfo mixin : mixins) {
			if ((mixin.node.access & Opcodes.ACC_INTERFACE) != 0) {
				providers.put(mixin.node.name, mixin);
			}
			for (String injected : mixin.node.interfaces) {
				providers.putIfAbsent(injected, mixin);
			}
		}

		boolean previous = IrisMixinPlugin.usingVulkan;
		IrisMixinPlugin plugin = new IrisMixinPlugin();
		try {
			Set<String> appliedOnVulkan = applied(plugin, mixins, true);
			Set<String> appliedOnOpenGl = applied(plugin, mixins, false);

			Deque<ClassNode> toScan = new ArrayDeque<>(vulkanClasses());
			Set<String> scanned = new HashSet<>();
			mixins.stream().filter(mixin -> appliedOnVulkan.contains(mixin.name)).forEach(mixin -> toScan.add(mixin.node));
			List<String> missing = new ArrayList<>();
			int casts = 0;
			while (!toScan.isEmpty()) {
				ClassNode owner = toScan.poll();
				if (!scanned.add(owner.name)) {
					continue;
				}
				for (MethodNode method : owner.methods) {
					for (AbstractInsnNode instruction : method.instructions) {
						if ((instruction.getOpcode() == Opcodes.CHECKCAST || instruction.getOpcode() == Opcodes.INSTANCEOF)
							&& instruction instanceof TypeInsnNode type && providers.containsKey(type.desc)) {
							casts++;
							MixinInfo provider = providers.get(type.desc);
							if (!appliedOnVulkan.contains(provider.name)) {
								missing.add(owner.name.replace('/', '.') + "#" + method.name + " casts to " + type.desc.replace('/', '.')
									+ ", which only " + provider.name + " (not applied on Vulkan) provides");
							}
						}
					}
				}
			}

			expect("shared code only casts to mixin interfaces that are applied on Vulkan", missing.isEmpty(), String.join("; ", missing));
			System.out.printf("  %d classes scanned, %d casts to mixin-provided types, %d unresolved on Vulkan%n", scanned.size(), casts, missing.size());
			missing.forEach(entry -> System.out.println("    MISSING " + entry));

			// Nothing Vulkan mode now applies may be dropped from OpenGL mode.
			for (MixinInfo mixin : mixins) {
				if (appliedOnVulkan.contains(mixin.name) && !mixin.name.contains("VKOnly")) {
					expect("OpenGL mode still applies " + mixin.name, appliedOnOpenGl.contains(mixin.name), "");
				}
			}

			for (String name : IrisMixinPlugin.BACKEND_NEUTRAL_MIXINS) {
				MixinInfo mixin = mixins.stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElse(null);
				expect("backend-neutral mixin " + name + " is listed in a mixin config", mixin != null, "");
				if (mixin != null) {
					String bytes = new String(classBytes(name), StandardCharsets.ISO_8859_1);
					for (String reference : OPENGL_REFERENCES) {
						expect("backend-neutral mixin " + name + " does not reference " + reference, !bytes.contains(reference), "");
					}
				}
			}
		} finally {
			IrisMixinPlugin.usingVulkan = previous;
		}
	}

	private static Set<String> applied(IrisMixinPlugin plugin, List<MixinInfo> mixins, boolean vulkan) {
		IrisMixinPlugin.usingVulkan = vulkan;
		Set<String> applied = new HashSet<>();
		for (MixinInfo mixin : mixins) {
			if (plugin.shouldApplyMixin(mixin.target, mixin.name)) {
				applied.add(mixin.name);
			}
		}
		return applied;
	}

	private static List<MixinInfo> loadMixins() throws IOException {
		List<MixinInfo> mixins = new ArrayList<>();
		for (String config : PLUGIN_CONFIGS) {
			JsonObject json;
			try (InputStream in = Iris.class.getClassLoader().getResourceAsStream(config)) {
				if (in == null) {
					throw new IllegalStateException("mixin config " + config + " not on the classpath");
				}
				json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			}
			String pkg = json.get("package").getAsString();
			for (String section : List.of("client", "mixins")) {
				JsonArray entries = json.has(section) ? json.getAsJsonArray(section) : new JsonArray();
				for (JsonElement entry : entries) {
					String name = pkg + "." + entry.getAsString();
					byte[] bytes = classBytes(name);
					if (bytes == null) {
						continue;
					}
					ClassNode node = new ClassNode();
					new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
					mixins.add(new MixinInfo(name, mixinTarget(node), node));
				}
			}
		}
		return mixins;
	}

	private static String mixinTarget(ClassNode node) {
		if (node.invisibleAnnotations != null) {
			for (var annotation : node.invisibleAnnotations) {
				if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") && annotation.values != null) {
					for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
						if (annotation.values.get(i).equals("value") && annotation.values.get(i + 1) instanceof List<?> targets && !targets.isEmpty()) {
							return ((org.objectweb.asm.Type) targets.getFirst()).getClassName();
						}
					}
				}
			}
		}
		return "";
	}

	private static List<ClassNode> vulkanClasses() throws IOException {
		Path root = Path.of(Iris.class.getProtectionDomain().getCodeSource().getLocation().getPath());
		List<ClassNode> classes = new ArrayList<>();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
				String name = root.relativize(file).toString().replace('\\', '/').replace(".class", "");
				String outer = name.contains("$") ? name.substring(0, name.indexOf('$')) : name;
				if (VULKAN_PACKAGES.stream().anyMatch(name::startsWith) || VULKAN_CLASSES.contains(outer)) {
					ClassNode node = new ClassNode();
					new ClassReader(Files.readAllBytes(file)).accept(node, ClassReader.SKIP_FRAMES);
					classes.add(node);
				}
			}
		}
		return classes;
	}

	private static byte[] classBytes(String className) throws IOException {
		try (InputStream in = Iris.class.getClassLoader().getResourceAsStream(className.replace('.', '/') + ".class")) {
			return in == null ? null : in.readAllBytes();
		}
	}

	/** The fallback behaviour HeldItemSupplier relies on once every Item implements IrisItemLightProvider. */
	private void checkItemLightDefaults() {
		System.out.println("== IrisItemLightProvider defaults (what MixinItem gives every Item)");
		net.minecraft.server.Bootstrap.bootStrap();
		// MixinItem adds no methods, so an item's behaviour is exactly the interface defaults.
		IrisItemLightProvider provider = new IrisItemLightProvider() {
		};

		ItemStack empty = ItemStack.EMPTY;
		expect("an empty hand holds AirItem", empty.getItem() instanceof AirItem, String.valueOf(empty.getItem()));
		expect("AirItem emits no light", provider.getLightEmission(null, empty) == 0, "");
		expect("AirItem has the default light colour", provider.getLightColor(null, empty).equals(new Vector3f(1, 1, 1)), "");

		// Offline there is no registry reload to bind default item components; ItemStack needs them bound, and an
		// empty map leaves BLOCK_STATE absent, i.e. the block's default state, as for an item fresh from the creative menu.
		for (var item : List.of(Items.GLOWSTONE, Items.STICK)) {
			item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
		}
		int glowstone = provider.getLightEmission(null, new ItemStack(Items.GLOWSTONE));
		expect("a glowstone block item emits 15", glowstone == 15, "got " + glowstone);
		int stick = provider.getLightEmission(null, new ItemStack(Items.STICK));
		expect("a stick (not a block item) emits 0", stick == 0, "got " + stick);
		System.out.printf("  air=%d glowstone=%d stick=%d colour(air)=%s%n", provider.getLightEmission(null, empty), glowstone, stick, provider.getLightColor(null, empty));
	}

	private void expect(String label, boolean condition, String detail) {
		checks++;
		if (!condition) {
			failure.accept("mixins: " + label + (detail.isEmpty() ? "" : " (" + detail + ")"));
		}
	}
}
