import org.gradle.process.JavaForkOptions

plugins {
    kotlin("jvm")
    application
}

group = "com.github.xyzboom"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
    implementation(project.rootProject)
    runtimeOnly(project(":runners:kotlin-runner"))
    runtimeOnly(project(":runners:scala-runner"))
    runtimeOnly(project(":runners:groovy-runner"))
    runtimeOnly(kotlin("stdlib"))
    runtimeOnly(kotlin("stdlib-jdk8"))
    runtimeOnly(kotlin("reflect"))
    runtimeOnly(kotlin("test"))
    runtimeOnly(kotlin("script-runtime"))
    runtimeOnly(kotlin("annotations-jvm"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testImplementation(testFixtures(project.rootProject))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
application {
    mainClass = "com.github.xyzboom.codesmith.CLFMain"
}
val jdk17Launcher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(17)
}
tasks.withType<JavaExec> {
    javaLauncher = jdk17Launcher
    workingDir = rootDir
    environment("JDK_17_0", jdk17Launcher.get().metadata.installationPath.asFile.absolutePath)
    systemProperties["codesmith.logger.console"] = System.getProperty("codesmith.logger.console") ?: "info"
    systemProperties["codesmith.logger.traceFile"] = System.getProperty("codesmith.logger.traceFile") ?: "trace"
    systemProperties["codesmith.logger.traceFile.ImmediateFlush"] =
        System.getProperty("codesmith.logger.traceFile.ImmediateFlush") ?: "true"
    val tmpPath = System.getProperty("java.io.tmpdir")
    if (tmpPath != null) {
        systemProperties["java.io.tmpdir"] = tmpPath
    }
    systemProperties["codesmith.logger.outdir"] = System.getProperty("codesmith.logger.outdir") ?: "./out/raw"

    systemProperty("idea.ignore.disabled.plugins", "true")
    systemProperty("idea.home.path", rootDir)

    doFirst {
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
        setLibraryProperty("kotlin.full.stdlib.path", "kotlin-stdlib")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib-jdk8", "kotlin-stdlib-jdk8")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
        setLibraryProperty("kotlin.reflect.jar.path", "kotlin-reflect")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")
    }
}

fun JavaForkOptions.setLibraryProperty(propName: String, jarName: String) {
    val path = project.configurations
        .runtimeClasspath.get()
        .files
        .find { """$jarName-\d.*jar""".toRegex().matches(it.name) }
        ?.absolutePath
        ?: return
    systemProperty(propName, path)
}