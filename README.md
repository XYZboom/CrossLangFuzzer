# CrossLangFuzzer
CrossLangFuzzer is an innovative fuzzing tool designed specifically for testing JVM-based language compilers.
Currently, it can generate structurally valid cross-language programs in Kotlin, Java, Groovy, Scala 2, 
and Scala 3 languages. Three mutators are designed to diversify the generated programs.

Using CrossLangFuzzer, we discovered 24 compilers bugs. The details is shown in [this repo](https://github.com/XYZboom/CrossLangFuzzerData)

# Usage
## Kotlin Runner
Kotlin test-related code is located in my forked Kotlin repository: 
[https://github.com/XYZboom/kotlin/tree/codesmith1.9.20/compiler/tests-common-new/tests/org/jetbrains/kotlin/test](https://github.com/XYZboom/kotlin/tree/codesmith1.9.20/compiler/tests-common-new/tests/org/jetbrains/kotlin/test)

## Groovy Runner
See groovy runner at [here](./runners/groovy-runner/src/main/java/com/github/xyzboom/codesmith/CodeSmithGroovyRunner.kt)

## Scala Runner
See groovy runner at [here](./runners/scala-runner/src/main/kotlin/com/github/xyzboom/codesmith/scala/CodeSmithScalaDifferentialTestRunner.kt)