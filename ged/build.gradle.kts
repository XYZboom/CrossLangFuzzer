plugins {
    kotlin("jvm")
}

group = "com.github.xyzboom"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    api("io.github.xyzboom:ged4clf:0.2.0")
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