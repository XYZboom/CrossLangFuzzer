plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    // this file is compiled from Kotlin Compiler at "generators/tree-generator-common"
    implementation(files(File(rootDir, "libs/tree-generator-common.jar")))
    testImplementation(kotlin("test"))
}