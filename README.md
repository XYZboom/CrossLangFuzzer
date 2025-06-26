# CrossLangFuzzer
CrossLangFuzzer is an innovative fuzzing tool designed specifically for testing JVM-based language compilers.
Currently, it can generate structurally valid cross-language programs in Kotlin, Java, Groovy, Scala 2, 
and Scala 3 languages. Currently three mutators are designed to diversify the generated programs.

Using CrossLangFuzzer, we discovered 24 compilers bugs. The details is shown in [this repo](https://github.com/XYZboom/CrossLangFuzzerData)

# Requirements
- git
- JDK 1.8(build 1.8.0_432-b06 to reproduce [JDK-8352290](https://bugs.openjdk.org/browse/JDK-8352290?filter=allissues))
- JDK 11
- JDK 17

As no bugs were found currently in JDK 11 and 17, you can use any build version of these two.
You should set `JDK_HOME=/path/to/jdk_1.8`, `JDK_HOME_11=/path/to/jdk_11` and `JDK_HOME_17=/path/to/jdk_17` to 
make sure the Kotlin Runner know where the JDK is.
The Kotlin Compiler will be tested directly in a forked Kotlin repository.
The Scala and Groovy Compiler will be tested through published jars.
These requirements will be installed automatically by gradle.

# Quick Run

This quick run section is for Groovy Runner, see [Usage](#usage) for more details.
First, clone [CrossLangFuzzer](https://github.com/XYZboom/CrossLangFuzzer)
```bash
git clone https://github.com/XYZboom/CrossLangFuzzer.git
cd CrossLangFuzzer
```
Then, run the Groovy Runner.
```bash
./gradlew :runners:groovy-runner:run --args="--gv 4.0.26,5.0.0-alpha-12" -Dorg.gradle.java.home=/path/to/jdk_greater_11
```
Replace `/path/to/jdk_over_11` to a JDK whose version greater than 11.

Gradle may take some time to download the required dependencies, approximately 5 to 20 minutes.

The runner **NEVER STOP**, press `Ctrl+C` to stop it manually.
If some bugs were detected, the output can be seen in `CrossLangFuzzer/runners/groovy-runner/out`

# Usage

First, clone [CrossLangFuzzer](https://github.com/XYZboom/CrossLangFuzzer)
```bash
git clone https://github.com/XYZboom/CrossLangFuzzer.git
```

We currently have three Runners, which are used for testing the Kotlin, Scala, and Groovy compilers.
In the Scala and Groovy Runners, the Java compiler being tested is provided by the JDK used to run the current Runner.
In the Kotlin Runner, it will be the JDK8 and JDK17 installed on the current device.
Detailed configuration methods will be explained in the corresponding sections for each Runner.
All runners **NEVER STOP**, you should stop the runner yourself.

## Kotlin Runner 
Currently, due to certain restrictions (see [KT-78477](https://youtrack.jetbrains.com/issue/KT-78477/Support-External-Compiler-Test-Suites-Outside-Kotlin-Repository)), 
we cannot use the Kotlin compiler's test suite outside of the Kotlin compiler itself. 
Therefore, some of the test code for Kotlin Runner is located within a forked Kotlin compiler repository.
It may take about 1-2 hours for Gradle to download the required dependencies **in Kotlin Runner**.
And about 15 minutes to compile the Kotlin Runner. We will make an independent Kotlin Runner in the future.

For Kotlin Runner, you should publish CrossLangFuzzer into your local maven repository.
```bash
cd CrossLangFuzzer
# in CrossLangFuzzer
./gradlew publishToMavenLocal
```

### Run using forked repository
Clone the forked Kotlin compiler repository and checkout the branch `crosslangfuzzer-dev-9df698e`
```bash
git clone https://github.com/XYZboom/kotlin
cd kotlin
git checkout crosslangfuzzer-dev-9df698e
```
(Strange name code-smith in this branch? Haha, that's because CrossLangFuzzer was previously called CodeSmith.)
- Run Differential Test
```bash
./gradlew :compiler:tests-common-new:test --tests "org.jetbrains.kotlin.test.CodeSmithDifferentialTest.test" -Dcodesmith.logger.outdir=/path/to/logdir
```
- Run Normal Test
```bash
./gradlew :compiler:tests-common-new:test --tests "org.jetbrains.kotlin.test.CodeSmithTest.test" -Dcodesmith.logger.outdir=/path/to/logdir
```

Remember to replace `/path/to/logdir` into your own path.
After a bug was detected, the output will be at `/path/to/logdir`.

### Run using official repository with patch
**Another way** to run tests with Kotlin Runner.
You can also check out 9df698e from official Kotlin repository and apply the patch
[CrossLangFuzzer-dev-9df698e.patch](runners/kotlin-runner/CrossLangFuzzer-dev-9df698e.patch).
```bash
git clone https://github.com/Jetbrains/kotlin
cd kotlin
git checkout 9df698e
git apply /path/to/CrossLangFuzzer/runners/kotlin-runner/CrossLangFuzzer-dev-9df698e.patch
```
Then you can run the tests as introduced above.

## Groovy Runner
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

## Scala Runner
```bash
./gradlew :runners:scala-runner:run
```
This will run the differential testing for Scala 2.13.15 and Scala 3.6.4-RC1-bin-20241231-1f0c576-NIGHTLY.
See [here](./runners/scala-runner/build.gradle.kts) for more Scala version information.