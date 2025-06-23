import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    // this file is compiled from Kotlin Compiler at "generators/tree-generator-common"
    implementation(files(File(rootDir, "libs/tree-generator-common.jar")))
    testImplementation(kotlin("test"))
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xmulti-dollar-interpolation", "-Xwhen-guards"))
}