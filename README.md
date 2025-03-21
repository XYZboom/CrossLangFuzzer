# CrossLangFuzzer
CrossLangFuzzer is an innovative fuzzing tool designed specifically for 
testing JVM-based language compilers. Unlike traditional fuzzing approaches that generate random inputs,
our tool produces structurally valid code snippets that combine elements from multiple JVM languages, 
exposing edge cases and interoperability issues in compiler implementations. 
Our data is at [CrossLangFuzzerData](https://github.com/XYZboom/CrossLangFuzzerData)

# Usage
## Kotlin Runner
Kotlin test-related code is located in my forked Kotlin repository: 
[https://github.com/XYZboom/kotlin/tree/codesmith1.9.20/compiler/tests-common-new/tests/org/jetbrains/kotlin/test](https://github.com/XYZboom/kotlin/tree/codesmith1.9.20/compiler/tests-common-new/tests/org/jetbrains/kotlin/test)

## Groovy Runner
See groovy runner at [here](./runners/groovy-runner/src/main/java/com/github/xyzboom/codesmith/CodeSmithGroovyRunner.kt)

## Scala Runner
See groovy runner at [here](./runners/scala-runner/src/main/kotlin/com/github/xyzboom/codesmith/scala/CodeSmithScalaDifferentialTestRunner.kt)