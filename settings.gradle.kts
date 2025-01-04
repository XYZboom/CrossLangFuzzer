plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "code-smith"
include("test-framework")
include("runners:scala-runner")
findProject(":runners:scala-runner")?.name = "scala-runner"
