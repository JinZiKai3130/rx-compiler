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
// compiler.mk renames Lexer.g4/Parser.g4 to RxLexer.g4/RxParser.g4 (the ANTLR
// runtime already owns the plain names). Do the same here so the Gradle model is
// self-contained and does not depend on build/antlr-src from a previous make run.
// Generated Java goes to .../antlr/main/rx/gen so that the package declaration
// matches the directory layout (Eclipse JDT / redhat.java requires this).

val grammarDir = layout.buildDirectory.dir("grammar")
val generatedJavaRoot = layout.buildDirectory.dir("generated-src/antlr/main")
val generatedJavaPackage = layout.buildDirectory.dir("generated-src/antlr/main/rx/gen")

val antlrTool: Configuration by configurations.creating

dependencies {
    antlrTool("org.antlr:antlr4:4.13.2")
    implementation("org.antlr:antlr4-runtime:4.13.2")
}

val prepareGrammar by tasks.registering {
    val outDir = grammarDir.get().asFile
    outputs.dir(outDir)
    doLast {
        outDir.mkdirs()
        val lexer = file("grammar/Lexer.g4").readText()
            .replace("lexer grammar Lexer;", "lexer grammar RxLexer;")
        File(outDir, "RxLexer.g4").writeText(lexer)

        val parser = file("grammar/Parser.g4").readText()
            .replace("parser grammar Parser;", "parser grammar RxParser;")
            .replace("tokenVocab=Lexer;", "tokenVocab=RxLexer;")
        File(outDir, "RxParser.g4").writeText(parser)
    }
}

val generateLexer by tasks.registering(JavaExec::class) {
    dependsOn(prepareGrammar)
    classpath = antlrTool
    mainClass.set("org.antlr.v4.Tool")
    inputs.dir(grammarDir)
    outputs.dir(generatedJavaRoot)
    doFirst { generatedJavaPackage.get().asFile.mkdirs() }
    args(
        "-visitor",
        "-package", "rx.gen",
        "-o", generatedJavaPackage.get().asFile.absolutePath,
        "-lib", grammarDir.get().asFile.absolutePath,
        grammarDir.get().asFile.resolve("RxLexer.g4").absolutePath,
    )
}

val generateParser by tasks.registering(JavaExec::class) {
    dependsOn(generateLexer)
    classpath = antlrTool
    mainClass.set("org.antlr.v4.Tool")
    inputs.dir(grammarDir)
    outputs.dir(generatedJavaRoot)
    doFirst { generatedJavaPackage.get().asFile.mkdirs() }
    args(
        "-visitor",
        "-package", "rx.gen",
        "-o", generatedJavaPackage.get().asFile.absolutePath,
        // The lexer run wrote RxLexer.tokens into the package output dir.
        "-lib", generatedJavaPackage.get().asFile.absolutePath,
        grammarDir.get().asFile.resolve("RxParser.g4").absolutePath,
    )
}

sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin")
        java.srcDir(generatedJavaRoot)
    }
}

tasks.compileKotlin {
    dependsOn(generateParser)
}
