plugins {
    kotlin("jvm") version "1.9.23"
    `maven-publish`
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
    implementation("org.reflections:reflections:0.10.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.+")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.+")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.+")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
    runtimeOnly("org.apache.logging.log4j:log4j-api:2.20.0")
    runtimeOnly("org.slf4j:slf4j-log4j12:2.0.16")
    runtimeOnly("org.slf4j:slf4j-api:2.0.16")
    // For coverage usage
    api("org.jacoco:org.jacoco.core:0.8.12")
    implementation(kotlin("reflect"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    systemProperties["codesmith.logger.console"] = System.getProperty("codesmith.logger.console") ?: "info"
    systemProperties["codesmith.logger.traceFile"] = System.getProperty("codesmith.logger.traceFile") ?: "off"
}
kotlin {
    jvmToolchain(8)
}