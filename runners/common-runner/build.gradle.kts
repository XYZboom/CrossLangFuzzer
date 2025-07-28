plugins {
    kotlin("jvm")
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
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testImplementation(testFixtures(project.rootProject))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}