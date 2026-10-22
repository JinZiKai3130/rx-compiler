// IDE-only project model. The real build stays on make/kotlinc (see compiler.mk).
pluginManagement {
    // Prefer Maven Central, which is usually faster than the Gradle Plugin
    // Portal from mainland China; the Kotlin plugin marker is published there.
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "rx-compiler"
