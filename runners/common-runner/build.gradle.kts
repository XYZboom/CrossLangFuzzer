plugins {
    kotlin("jvm")
}

group = "com.github.xyzboom"
version = "1.0-SNAPSHOT"

dependencies {
    api("com.github.ajalt.clikt:clikt:5.0.3")
    implementation(project.rootProject)
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}