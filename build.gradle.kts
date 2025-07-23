import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.21"
    `maven-publish`
    `java-test-fixtures`
}

group = "com.github.xyzboom"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}
publishing {
    repositories {
        mavenLocal()
    }
    publications {
        create<MavenPublication>("CodeSmith") {
            groupId = "com.github.XYZboom"
            artifactId = "CodeSmith"
            version = "1.0-SNAPSHOT"

            from(components["java"])
        }
        create<MavenPublication>("CodeSmith-source") {
            groupId = "com.github.XYZboom"
            artifactId = "CodeSmith"
            version = "1.0-SNAPSHOT"

            // 配置要上传的源码
            artifact(tasks.register<Jar>("sourcesJar") {
                from(sourceSets.main.get().allSource)
                archiveClassifier.set("sources")
            }) {
                classifier = "sources"
            }
        }
    }
}
dependencies {
    api(project(":tree"))
    implementation("org.reflections:reflections:0.10.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.+")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.+")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.+")
    // OK, the new IR tree will use gson but the old still using jackson.
    api("com.google.code.gson:gson:2.13.1")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.20.0")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")
    // For coverage usage
    api("org.jacoco:org.jacoco.core:0.8.12")
    implementation(kotlin("reflect"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testFixturesApi(kotlin("test"))
    testFixturesApi("io.kotest:kotest-assertions-core:5.9.1")
}

tasks.test {
    useJUnitPlatform()
    systemProperties["codesmith.logger.console"] = System.getProperty("codesmith.logger.console") ?: "info"
    systemProperties["codesmith.logger.traceFile"] = System.getProperty("codesmith.logger.traceFile") ?: "off"
}
kotlin {
    jvmToolchain(8)
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xwhen-guards"))
}

tasks.register<JavaExec>("generateDefaultConfigFile") {
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
    mainClass.set("com.github.xyzboom.codesmith.GenDefaultConfigFileKt")
}