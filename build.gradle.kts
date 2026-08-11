plugins {
    // Applies the correct Loom variant (obfuscated vs. unobfuscated) for the active version.
    id("dev.kikugie.loom-back-compat")
    id("maven-publish")
}

// DO NOT set group = ...! It's inherited from stonecutter.properties.toml via mod.group.
// Resolved at the project level, not inside tasks {} — Task also has a property(String) method,
// which would otherwise shadow Project.property(String) and fail to find these.
val modId: String = property("mod.id") as String
val modName: String = property("mod.name") as String
val modVersion: String = property("mod.version") as String
val mcCompat: String = sc.properties["mod.mc_compat"] as String

version = "$modVersion+mc${sc.current.version}"
base.archivesName = modId

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    // Pin each mod's group to the repo that actually publishes it, and prefer Modrinth's maven
    // mirror over Terraformers' own host for ModMenu — maven.terraformersmc.com has had repeated
    // sustained outages (502s), while Modrinth mirrors the same artifact under the "maven.modrinth"
    // group (same jar, just published there too; see https://support.modrinth.com/en/articles/8801191).
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.isxander.dev/releases") { name = "isxander-releases" } }
        filter { includeGroup("dev.isxander") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.quiltmc.org/repository/release/") { name = "Quilt" } }
        filter { includeGroup("org.quiltmc.parsers") }
    }
}

dependencies {
    // To change the versions see stonecutter.properties.toml
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Applies Mojang mappings on obfuscated versions; no-op on 26.1+ where Minecraft ships unobfuscated.
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${sc.properties["deps.fabric_loader"] as String}")

    // Fabric API. This is technically optional, but you probably want it anyway.
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties["deps.fabric_api"] as String}")

    // ModMenu API (compile only) — via Modrinth's maven mirror, see repositories { } above
    modCompileOnly("maven.modrinth:modmenu:${sc.properties["deps.modmenu"] as String}")

    // YetAnotherConfigLib (YACL) API
    modImplementation("dev.isxander:yet-another-config-lib:${sc.properties["deps.yacl"] as String}")
}

java {
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present. If you remove this line, sources will not be generated.
    withSourcesJar()

    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = requiredJava.majorVersion.toInt()
    }

    processResources {
        val props = mapOf(
            "id" to modId,
            "name" to modName,
            "version" to modVersion,
            "minecraft" to mcCompat,
            "java" to "${requiredJava.majorVersion}"
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        inputs.property("mixinJava", mixinJava)
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    jar {
        val projectName = project.name
        inputs.property("projectName", projectName)

        from("LICENSE") { rename { "${it}_$projectName" } }
    }

    // Builds every active version and copies the resulting jars into build/libs/<mod version>/
    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to build/libs/{mod version}/"

        inputs.property("version", modVersion)
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/$modVersion"))
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
    }
}
