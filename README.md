# CrossLangFuzzer
CrossLangFuzzer is an innovative fuzzing tool designed specifically for testing JVM-based language compilers.
It currently supports generating structurally valid cross-language programs in Kotlin,
Java, Groovy, Scala 2, and Scala 3.
Several mutators have been designed to diversify the generated programs.

The generator, mutator, and reducer are all language-agnostic. Only the translator (which translates IR into source code for different languages) and the test executor are language-specific. The test executors are currently isolated in separate modules because there are some unresolved dependency conflicts among the different executors.

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

The tree generator sources are fetched from Maven Central as `io.github.xyzboom:tree-generator:0.1.0:sources` and compiled during the build; no prebuilt generator JAR or manual download is needed. Source provenance is documented in [BaseFuzzerTreeGen](https://github.com/XYZboom/BaseFuzzerTreeGen).

### Kotlin Runner 

```bash
# in CrossLangFuzzer
./gradlew :runners:kotlin-runner:run --args="-s" -Dorg.gradle.java.home=/path/to/jdk_greater_11
```
Replace `/path/to/jdk_over_11` to a JDK whose version greater than 11.
Once a bug is detected, the output will be shown in `CrossLangFuzzer/out/min`

Run `./gradlew :runners:kotlin-runner:run --args="-s" -Dorg.gradle.java.home=/path/to/jdk_greater_11`
for full command line usage.

### Incremental K2 Testing

The incremental mode compares retained-state K2 compilations with clean compilations of the same source snapshots.
Set `JDK_17_0` to an installed JDK 17 home for the Kotlin compiler test harness (unless the Gradle JVM itself is JDK 17; that case is auto-detected), then run a bounded smoke test. This is a Kotlin-runner harness requirement; other language runners are unaffected.

```bash
mkdir -p out
export JDK_17_0=/path/to/jdk-17
set -o pipefail
./gradlew -Dorg.gradle.java.home=/path/to/jdk-11-or-newer :runners:kotlin-runner:run --args="--mode incremental --config-file config/default-kotlin-bytecode.json --max-rounds 64 --seed 20261400 --schedule-repeats 1 --scc-stage-count 3 --state-change-steps 11 --incremental-compiles-per-round 4 --out out/incremental-smoke" 2>&1 | tee out/incremental-smoke.log
```

`--max-rounds` is the compiler-submission budget, not the number of logical test rounds. A full logical round has four retained-state incremental compilations, each paired with a clean compile (eight submissions total); the final budget can leave a partial round. The sample's 64-submission limit allows at most eight complete rounds; set an explicit smaller budget for CI because runtime varies by machine, JDK, and generated inputs. Omit `--stop-on-errors` for a bounded campaign that should continue collecting outcomes. Snapshot sources, compiler results/diagnostics, binary comparisons, and IR are written under the selected `--out` directory, in `incremental/run-<timestamp>-seed-<seed>/`; `tee` above saves the console log separately.

The differential oracle treats two failed builds as provisionally equivalent while retaining their diagnostics; one success and one failure is a mismatch; two successful builds are compared by class inventory and bytecode. For larger campaigns, increase `--max-rounds`, use a fresh `--out` directory, and preserve the seed and configuration for replay.

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
32 compiler bugs are found by CrossLangFuzzer. The details are shown in [this repo](https://github.com/XYZboom/CrossLangFuzzerData).

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
| Kotlin  | [KT-78819](https://youtrack.jetbrains.com/issue/KT-78819)                   |
| Kotlin  | [KT-79508](https://youtrack.jetbrains.com/issue/KT-79508)                   |
| Kotlin  | [KT-80382](https://youtrack.jetbrains.com/issue/KT-80382)                   |
| Kotlin  | [KT-80387](https://youtrack.jetbrains.com/issue/KT-80387)                   |
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
| Java    | [JDK-8347330](https://bugs.openjdk.org/browse/JDK-8347330?filter=allissues) |
| Java    | [JDK-8352290](https://bugs.openjdk.org/browse/JDK-8352290?filter=allissues) |
| Java    | [JDK-8361835](https://bugs.openjdk.org/browse/JDK-8361835?filter=allissues) |
| Java    | [JDK-8370716](https://bugs.openjdk.org/browse/JDK-8370716?filter=allissues) |