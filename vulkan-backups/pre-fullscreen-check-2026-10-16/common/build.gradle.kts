plugins {
    id("java")
    id("idea")
    id("net.fabricmc.fabric-loom") version("1.15.4")
    id("com.github.gmazzo.buildconfig") version "5.3.5"
}

repositories {
    mavenLocal()
    maven("https://maven.parchmentmc.org/")
    maven {
        name = "caffeinemcRepositoryReleases"
        url = uri("https://maven.caffeinemc.net/releases")
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

val MINECRAFT_VERSION: String by rootProject.extra
val PARCHMENT_VERSION: String? by rootProject.extra
val FABRIC_LOADER_VERSION: String by rootProject.extra
val SODIUM_DEPENDENCY_FABRIC: Any by rootProject.extra
val FABRIC_API_VERSION: String by rootProject.extra

sourceSets.create("desktop")

buildConfig {
    className("BuildConfig")   // forces the class name. Defaults to 'BuildConfig'
    packageName("net.irisshaders.iris")  // forces the package. Defaults to '${project.group}'
    useJavaOutput()

    // TODO hook this up
    buildConfigField("IS_SHARED_BETA", false)
    buildConfigField("ACTIVATE_RENDERDOC", false)
    buildConfigField("BETA_TAG", "")
    buildConfigField("BETA_VERSION", 0)

    sourceSets.getByName("desktop") {
        buildConfigField("IS_SHARED_BETA", false)
    }
}

dependencies {
    minecraft(group = "com.mojang", name = "minecraft", version = MINECRAFT_VERSION)

    implementation("net.fabricmc:fabric-loader:$FABRIC_LOADER_VERSION")

    compileOnly(fabricApi.module("fabric-resource-loader-v1", FABRIC_API_VERSION))
    compileOnly(fabricApi.module("fabric-block-getter-api-v2", FABRIC_API_VERSION))
    compileOnly(fabricApi.module("fabric-renderer-api-v1", FABRIC_API_VERSION))

    implementation(SODIUM_DEPENDENCY_FABRIC)
    compileOnly("org.antlr:antlr4-runtime:4.13.1")
    compileOnly("io.github.douira:glsl-transformer:3.0.0-pre3")
    compileOnly("org.anarres:jcpp:1.4.14")

    compileOnly(files(rootDir.resolve("DHApi.jar")))
}

afterEvaluate {
    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-Xmaxerrs")
        options.compilerArgs.add("2000")
    }
}

val vendoredJar by tasks.registering(Jar::class) {
    from(sourceSets.getByName("vendored").output)
    archiveClassifier.set("vendored")
}

val apiJar by tasks.registering(Jar::class) {
    from(sourceSets.getByName("api").output)
    archiveClassifier.set("api")
}

val headersJar by tasks.registering(Jar::class) {
    from(sourceSets.getByName("headers").output)
    archiveClassifier.set("headers")
}

configurations {
    register("vendoredJar") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    register("apiJar") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    register("headersJar") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
}

sourceSets {
    val main = getByName("main")
    val headers = create("headers")
    val api = create("api")
    val vendored = create("vendored")
    val desktop = getByName("desktop")

    headers.apply {
        java {
            compileClasspath += main.compileClasspath
        }
    }

    vendored.apply {
        java {
            compileClasspath += main.compileClasspath
        }
    }

    api.apply {
        java {
            compileClasspath += main.compileClasspath
        }
    }

    desktop.apply {
        java {
            srcDir("src/desktop/java")
        }
    }

    main.apply {
        java {
            compileClasspath += headers.output
            compileClasspath += api.output
            compileClasspath += vendored.output
            runtimeClasspath += api.output
            runtimeClasspath += vendored.output
        }
    }
}

artifacts {
    add("vendoredJar", vendoredJar)
    add("apiJar", apiJar)
    add("headersJar", headersJar)
}

loom {
    mixin {
        defaultRefmapName = "iris.refmap.json"
        useLegacyMixinAp = false
    }

    accessWidenerPath = file("src/main/resources/iris.accesswidener")

    mods {
        val main by creating { // to match the default mod generated for Forge
            sourceSet("vendored")
            sourceSet("main")
        }
    }
}

tasks {
    processResources {
        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to project.version))
        }
    }
    getByName<JavaCompile>("compileDesktopJava") {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }

    jar {
        from(rootDir.resolve("LICENSE.md"))

        val vendored = sourceSets.getByName("vendored")
        from(vendored.output.classesDirs)
        from(vendored.output.resourcesDir)

        val api = sourceSets.getByName("api")
        from(api.output.classesDirs)
        from(api.output.resourcesDir)

        val desktop = sourceSets.getByName("desktop")
        from(desktop.output.classesDirs)
        from(desktop.output.resourcesDir)

        manifest.attributes["Main-Class"] = "net.irisshaders.iris.LaunchWarn"
    }
}

// Offline validation of the Vulkan screen-pass shader path: Iris's real pack loader and TransformPatcher, then the
// Vulkan translator, compiled and reflected with the shaderc/SPIRV-Cross settings RenderPearl 26.3 uses.
// Extra packs (e.g. Complementary): ./gradlew :common:vulkanShaderCheck -PirisVulkanPacks=/path/a.zip,/path/b
val vulkanCheck = sourceSets.create("vulkanCheck")

val vulkanCheckStandardMacros by tasks.registering(Sync::class) {
    // StandardMacros reads the mipmap level from the running Minecraft instance, which does not exist offline.
    // This copy differs only in that expression; the build fails if the expression ever changes.
    val source = file("src/main/java/net/irisshaders/iris/gl/shader/StandardMacros.java")
    val expression = "Minecraft.getInstance().options.mipmapLevels().get()"
    from(source)
    into(layout.buildDirectory.dir("generated/vulkanCheck/net/irisshaders/iris/gl/shader"))
    filter { line: String -> line.replace(expression, "4") }
    doFirst {
        if (!source.readText().contains(expression)) {
            throw GradleException("vulkanShaderCheck: StandardMacros no longer contains '$expression'; update the override")
        }
    }
}

vulkanCheck.apply {
    java {
        srcDir(layout.buildDirectory.dir("generated/vulkanCheck"))
        compileClasspath += sourceSets.getByName("main").output + sourceSets.getByName("main").compileClasspath
        // Own classes first so the StandardMacros override shadows the production copy.
        runtimeClasspath = output + sourceSets.getByName("main").output + sourceSets.getByName("main").runtimeClasspath +
            configurations.getByName("vulkanCheckRuntimeClasspath")
    }
}

dependencies {
    "vulkanCheckImplementation"("org.antlr:antlr4-runtime:4.13.1")
    "vulkanCheckImplementation"("io.github.douira:glsl-transformer:3.0.0-pre3")
    "vulkanCheckImplementation"("org.anarres:jcpp:1.4.14")
}

tasks.named<JavaCompile>("compileVulkanCheckJava") {
    dependsOn(vulkanCheckStandardMacros)
}

val vulkanShaderCheck by tasks.registering(JavaExec::class) {
    description = "Translates and compiles shader pack screen passes for RenderPearl's Vulkan backend, offline."
    classpath = vulkanCheck.runtimeClasspath
    mainClass.set("net.irisshaders.iris.vulkan.VulkanShaderCheck")
    val work = layout.buildDirectory.dir("vulkanShaderCheck").get().asFile
    args(rootDir.resolve("testpacks/IrisVulkanSmoke").absolutePath, work.absolutePath)
    (project.findProperty("irisVulkanPacks") as String?)?.split(",")?.forEach { args(it.trim()) }
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + work.resolve("natives").absolutePath)
}

tasks.named("check") {
    dependsOn(vulkanShaderCheck)
}

// This trick hides common tasks in the IDEA list.
tasks.configureEach {
    group = null
}
