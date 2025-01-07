plugins {
    kotlin("jvm")
}

group = "com.github.xyzboom"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
    runtimeOnly("org.apache.logging.log4j:log4j-api:2.20.0")
    runtimeOnly("org.slf4j:slf4j-log4j12:2.0.16")
    runtimeOnly("org.slf4j:slf4j-api:2.0.16")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(8)
}

val generatePrinterTest = tasks.register<JavaExec>("GeneratePrinterTest") {
    inputs.dir("${rootProject.rootDir}/src/testData")
    group = "generate"
    mainClass = "com.github.xyzboom.codesmith.CodeSmithPrinterTestGenerator"
    workingDir = rootProject.rootDir
    classpath = sourceSets["main"].runtimeClasspath
}

rootProject.tasks.withType<Test> {
    dependsOn(generatePrinterTest)
}