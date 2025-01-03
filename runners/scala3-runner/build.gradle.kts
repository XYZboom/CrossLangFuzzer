plugins {
    kotlin("jvm") version "1.9.23"
}

group = "com.github.xyzboom"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project.rootProject)
    implementation("org.scala-lang:scala3-compiler_3:3.6.4-RC1-bin-20241231-1f0c576-NIGHTLY")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}