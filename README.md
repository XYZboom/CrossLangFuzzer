# CrossLangFuzzer
CrossLangFuzzer is an innovative fuzzing tool designed specifically for testing JVM-based language compilers.
It currently supports generating structurally valid cross-language programs in Kotlin,
Java, Groovy, Scala 2, and Scala 3.
Three mutators have been designed to diversify the generated programs.

# Quick Run

This quick run section is now only for Kotlin Runner 
(Other runners for quick run are still in development, 
see [#18](https://github.com/XYZboom/CrossLangFuzzer/issues/18) and 
[#20](https://github.com/XYZboom/CrossLangFuzzer/issues/20)).
For more information, please refer to [Build Project](#build-project)

First, install [docker](https://docs.docker.com/get-started/get-docker).

Pull and run image:
```bash
docker pull xyzboom123/clf:dev
docker run -it xyzboom123/clf:dev
```

In docker, run `quick_run.sh`
```bash
./quick_run.sh
```

Once a compiler bug is found, the quick run script will stop.
The bug found by CrossLangFuzzer will be reported at `CrossLangFuzzer/out/min`.

# Build Project
## Requirements
- Git (to clone project)
- JDK 1.8 (to run kotlin runner)
- JDK 11 (to build project)
- JDK 17 (to run kotlin runner)

Other requirements will be installed automatically by gradle.

Note that we may need JDK 1.8 build 1.8.0_432-b06 to reproduce [JDK-8352290](https://bugs.openjdk.org/browse/JDK-8352290?filter=allissues)).
Due to the difficulty of installing old JDK builds on Linux, you can use the latest build version, 
but we cannot guarantee that this issue can be reproduced(Other issues can be guaranteed to be reproduced).
Some JDK builds are also available at [azul](https://www.azul.com/downloads/?package=jdk#zulu).

## Build

First, clone [CrossLangFuzzer](https://github.com/XYZboom/CrossLangFuzzer)
```bash
git clone https://github.com/XYZboom/CrossLangFuzzer.git
```

We currently have three Runners, which are used for testing the Kotlin, Scala, and Groovy compilers.
In the Scala and Groovy Runners, the Java compiler being tested is provided by the JDK used to run the current Runner.
In the Kotlin Runner, it will be the JDK8 and JDK17 installed on the current device.
Detailed configuration methods will be explained in the corresponding sections for each Runner.

Download [tree-generator](https://github.com/XYZboom/CrossLangFuzzer/releases/download/dev-ef4368/tree-generator-common.jar)
which compiled from kotlin compiler into "libs/tree-generator-common.jar". 
See [KT-81261](https://youtrack.jetbrains.com/issue/KT-81261/Request-to-publish-tree-generator-module-as-a-standalone-library)
for more information.

### Kotlin Runner 

```bash
# in CrossLangFuzzer
./gradlew :runners:kotlin-runner:run --args="-s" -Dorg.gradle.java.home=/path/to/jdk_greater_11
```
Replace `/path/to/jdk_over_11` to a JDK whose version greater than 11.
Once a bug is detected, the output will be shown in `CrossLangFuzzer/out/min`

Run `./gradlew :runners:kotlin-runner:run --args="-s" -Dorg.gradle.java.home=/path/to/jdk_greater_11`
for full command line usage.

### Groovy Runner
```bash
# in CrossLangFuzzer
./gradlew :runners:groovy-runner:run --args="--gv 4.0.26,5.0.0-alpha-12" -Dorg.gradle.java.home=/path/to/jdk_greater_11
```
Replace `/path/to/jdk_over_11` to a JDK whose version greater than 11.
`--gv` means the groovy version you want to test. 
If two versions were given, the runner will run differential testing. 
Otherwise, the runner will run normal testing.
Currently, we only support `4.0.24`, `4.0.26`, `5.0.0-alpha-11` and `5.0.0-alpha-12`.
You can add more new versions [here](./runners/groovy-runner/src/main/resources/groovyJars)
Once a bug is detected, the output will be shown in `CrossLangFuzzer/runners/groovy-runner/out`

### Scala Runner
```bash
./gradlew :runners:scala-runner:run
```
This will run the differential testing for Scala 2.13.15 and Scala 3.6.4-RC1-bin-20241231-1f0c576-NIGHTLY.
See [here](./runners/scala-runner/build.gradle.kts) for more Scala version information.
Once a bug is detected, the output will be shown in `CrossLangFuzzer/runners/scala-runner/out`

# Bugs Found by CrossLangFuzzer
24 compiler bugs are found by CrossLangFuzzer. The details are shown in [this repo](https://github.com/XYZboom/CrossLangFuzzerData).

This table shows the bugs we have found.

| Project | Bug ID                                                                      |
|---------|-----------------------------------------------------------------------------|
| Kotlin  | [KT-74109](https://youtrack.jetbrains.com/issue/KT-74109)                   |
| Kotlin  | [KT-74147](https://youtrack.jetbrains.com/issue/KT-74147)                   |
| Kotlin  | [KT-74148](https://youtrack.jetbrains.com/issue/KT-74148)                   |
| Kotlin  | [KT-74151](https://youtrack.jetbrains.com/issue/KT-74151)                   |
| Kotlin  | [KT-74156](https://youtrack.jetbrains.com/issue/KT-74156)                   |
| Kotlin  | [KT-74160](https://youtrack.jetbrains.com/issue/KT-74160)                   |
| Kotlin  | [KT-74174](https://youtrack.jetbrains.com/issue/KT-74174)                   |
| Kotlin  | [KT-74166](https://youtrack.jetbrains.com/issue/KT-74166)                   |
| Kotlin  | [KT-74202](https://youtrack.jetbrains.com/issue/KT-74202)                   |
| Kotlin  | [KT-74209](https://youtrack.jetbrains.com/issue/KT-74209)                   |
| Kotlin  | [KT-74288](https://youtrack.jetbrains.com/issue/KT-74288)                   |
| Groovy  | [GROOVY-11548](https://issues.apache.org/jira/browse/GROOVY-11548)          |
| Groovy  | [GROOVY-11549](https://issues.apache.org/jira/browse/GROOVY-11549)          |
| Groovy  | [GROOVY-11550](https://issues.apache.org/jira/browse/GROOVY-11550)          |
| Groovy  | [GROOVY-11579](https://issues.apache.org/jira/browse/GROOVY-11579)          |
| Scala3  | [SCALA3-22307](https://github.com/scala/scala3/issues/22307)                |
| Scala3  | [SCALA3-22308](https://github.com/scala/scala3/issues/22308)                |
| Scala3  | [SCALA3-22309](https://github.com/scala/scala3/issues/22309)                |
| Scala3  | [SCALA3-22310](https://github.com/scala/scala3/issues/22310)                |
| Scala3  | [SCALA3-22311](https://github.com/scala/scala3/issues/22311)                |
| Scala3  | [SCALA3-22312](https://github.com/scala/scala3/issues/22312)                |
| Scala3  | [SCALA3-22717](https://github.com/scala/scala3/issues/22717)                |
| Scala2  | [SCALA2-13074](https://github.com/scala/bug/issues/13074)                   |
| Scala2  | [SCALA2-13075](https://github.com/scala/bug/issues/13075)                   |
| Java    | [JDK-8352290](https://bugs.openjdk.org/browse/JDK-8352290?filter=allissues) |