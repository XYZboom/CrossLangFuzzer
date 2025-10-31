plugins {
    id("com.gradleup.shadow") version "8.3.0"
    kotlin("jvm")
    application
}

group = "com.github.xyzboom"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://redirector.kotlinlang.org/maven/bootstrap")
    mavenLocal()
}

val kotlinVersion = "2.1.20-Beta1"

dependencies {
    implementation(project.rootProject)
    implementation(project(":runners:common-runner"))
    implementation(kotlin("compiler-internal-test-framework", kotlinVersion))
    implementation(kotlin("compiler", kotlinVersion))
    runtimeOnly(kotlin("test-junit5"))
    runtimeOnly("junit:junit:4.13.2")
    runtimeOnly(kotlin("reflect"))
    runtimeOnly(kotlin("test"))
    runtimeOnly(kotlin("script-runtime"))
    runtimeOnly(kotlin("annotations-jvm"))

    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}
tasks.register<JavaExec>("minimize") {
    dependsOn("build")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.github.xyzboom.codesmith.kotlin.MinimizeMain")
}

application {
    mainClass = "com.github.xyzboom.codesmith.kotlin.CrossLangFuzzerKotlinRunnerKt"
}

tasks.withType<JavaExec> {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(11)
    }
    systemProperties["codesmith.logger.console"] = System.getProperty("codesmith.logger.console") ?: "info"
    systemProperties["codesmith.logger.traceFile"] = System.getProperty("codesmith.logger.traceFile") ?: "trace"
    systemProperties["codesmith.logger.traceFile.ImmediateFlush"] =
        System.getProperty("codesmith.logger.traceFile.ImmediateFlush") ?: "true"
    val tmpPath = System.getProperty("java.io.tmpdir")
    if (tmpPath != null) {
        systemProperties["java.io.tmpdir"] = tmpPath
    }
    systemProperties["codesmith.logger.outdir"] = System.getProperty("codesmith.logger.outdir") ?: "./out/raw"

    workingDir = rootDir
    // Properties required to run the internal test framework.
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
    setLibraryProperty("kotlin.full.stdlib.path", "kotlin-stdlib") // k1
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib-jdk8", "kotlin-stdlib-jdk8")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
    setLibraryProperty("kotlin.reflect.jar.path", "kotlin-reflect") // k1
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")

    systemProperty("idea.ignore.disabled.plugins", "true")
    systemProperty("idea.home.path", rootDir)
}

fun JavaExec.setLibraryProperty(propName: String, jarName: String) {
    val path = project.configurations
        .runtimeClasspath.get()
        .files
        .find { """$jarName-\d.*jar""".toRegex().matches(it.name) }
        ?.absolutePath
        ?: return
    systemProperty(propName, path)
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "com.github.xyzboom.codesmith.kotlin.CrossLangFuzzerKotlinRunnerKt"
    }
}