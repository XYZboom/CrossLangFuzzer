plugins {
    kotlin("jvm")
}

group = "com.github.xyzboom"
version = "1.0-SNAPSHOT"

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