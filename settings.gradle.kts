pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Multi-version orchestration. Check latest at https://stonecutter.kikugie.dev/blog/changes
    id("dev.kikugie.stonecutter") version "0.9.6"

    // Bridges the Loom API differences between obfuscated (<26.1) and unobfuscated (26.1+) Minecraft
    // so a single build.gradle.kts works for every targeted version.
    // See https://codeberg.org/KikuGie/loom-back-compat
    id("dev.kikugie.loom-back-compat") version "0.4"

    // Auto-provisions the JDK toolchains required per version (21 for 1.21.11, 25 for 26.1+)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // See https://stonecutter.kikugie.dev/wiki/start/#choosing-minecraft-versions
        versions("1.21.11", "26.1.2", "26.2")
        vcsVersion = "1.21.11"
    }
}

// Should match your modid
rootProject.name = "skyblock_translator"
