plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "code-smith"
include("test-framework")
include("runners:scala3-runner")
findProject(":runners:scala3-runner")?.name = "scala3-runner"
