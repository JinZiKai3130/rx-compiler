// IDE-only Gradle project model.
//
// The actual compiler build is driven by `make` / `compiler.mk` and uses the
// kotlinc on PATH. This file exists so that Kotlin language servers (fwcd.kotlin
// / kotlin-language-server) get a proper multi-file project model instead of
// falling back to their experimental standalone mode.
//
// Keep the Kotlin plugin at the version bundled by kotlin-language-server
// 1.3.13 (Kotlin 2.1.0). A newer plugin would put newer-metadata stdlib jars on
// the IDE classpath, which that server cannot read.

plugins {
    kotlin("jvm") version "2.1.0"
}

// The Make build owns build/ (build/classes, build/kotlin-classes, ...).
// Keep Gradle's own outputs out of the way so the two never clobber each other.
layout.buildDirectory.set(layout.projectDirectory.dir("build/gradle-ide"))

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

// --- ANTLR sources for the IDE ------------------------------------------------
// The generated parser lives in src/main/java/rx/gen and is committed so that
// Maven can build without the ANTLR tool (see pom.xml). compiler.mk still
// regenerates it from grammar/ for the make build; after changing a grammar,
// run `make compiler` and copy the six build/antlr-src/*.java files into
// src/main/java/rx/gen.

dependencies {
    implementation("org.antlr:antlr4-runtime:4.13.2")
}

sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin")
        // Generated parser sources are regular Java under src/main/java.
        java.srcDir("src/main/java")
    }
}
