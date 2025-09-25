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
    implementation(project.rootProject)
    implementation(project(":runners:common-runner"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(11)
    }
    mainClass.set("com.github.xyzboom.codesmith.groovy.CodeSmithGroovyRunnerKt")
    systemProperties["codesmith.logger.console"] = System.getProperty("codesmith.logger.console") ?: "info"
    systemProperties["codesmith.logger.traceFile"] = System.getProperty("codesmith.logger.traceFile") ?: "off"
    systemProperties["codesmith.logger.traceFile.ImmediateFlush"] =
        System.getProperty("codesmith.logger.traceFile.ImmediateFlush") ?: "false"
    val tmpPath = System.getProperty("java.io.tmpdir")
    if (tmpPath != null) {
        systemProperties["java.io.tmpdir"] = tmpPath
    }
    systemProperties["codesmith.logger.outdir"] = System.getProperty("codesmith.logger.outdir") ?: "./out"
}