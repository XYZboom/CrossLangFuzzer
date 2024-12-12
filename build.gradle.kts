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
    // For coverage usage
    implementation("org.jacoco:org.jacoco.core:0.8.12")
    implementation(kotlin("reflect"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(8)
}