plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "code-smith"
include("test-framework")
include("runners:scala-runner")
findProject(":runners:scala-runner")?.name = "scala-runner"
include("runners:common-runner")
findProject(":runners:common-runner")?.name = "common-runner"
include("runners:groovy-runner")
findProject(":runners:groovy-runner")?.name = "groovy-runner"
include("runners:kotlin-runner")
findProject(":runners:kotlin-runner")?.name = "kotlin-runner"
include("tree")
include("tree:tree-generator")