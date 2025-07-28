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
    implementation("org.scala-lang:scala3-compiler_3:3.6.4-RC1-bin-20241231-1f0c576-NIGHTLY")
    implementation("org.scala-lang:scala-compiler:2.13.15")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}

tasks.withType<JavaExec> {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(11)
    }
    mainClass.set("com.github.xyzboom.codesmith.scala.CodeSmithScalaDifferentialTestRunnerKt")
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