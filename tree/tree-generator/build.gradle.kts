import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

val treeGeneratorSourceArchive by configurations.creating {
    isTransitive = false
}

val unpackTreeGeneratorSources by tasks.registering(Sync::class) {
    from({ zipTree(treeGeneratorSourceArchive.singleFile) })
    into(layout.buildDirectory.dir("generated/tree-generator-sources"))
}

kotlin.sourceSets.getByName("main").kotlin.srcDir(unpackTreeGeneratorSources)

dependencies {
    treeGeneratorSourceArchive("io.github.xyzboom:tree-generator:0.1.0:sources")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xmulti-dollar-interpolation", "-Xwhen-guards"))
}