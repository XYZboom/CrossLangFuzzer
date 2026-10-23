package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExecutableFunctionCallHarnessTest {
    @Test
    fun `K2 executes every guarded generated call and box returns OK`() {
        val nullableA = buildClassDeclaration {
            name = "NullableA"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val nullableB = buildClassDeclaration {
            name = "NullableB"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val library = buildModule {
            name = "library"
            classes += nullableA
            classes += nullableB
            classes += buildClassDeclaration {
                name = "KotlinOwner"
                language = Language.KOTLIN
                classKind = ClassKind.FINAL
                functions += throwingFunction("first", Language.KOTLIN)
                functions += throwingFunction("second", Language.KOTLIN)
                functions += throwingFunction(
                    "nullableOverload",
                    Language.KOTLIN,
                    buildNullableType { innerType = nullableA.type },
                )
                functions += throwingFunction(
                    "nullableOverload",
                    Language.KOTLIN,
                    buildNullableType { innerType = nullableB.type },
                )
            }
            classes += buildClassDeclaration {
                name = "JavaOwner"
                language = Language.JAVA
                classKind = ClassKind.FINAL
                functions += throwingFunction("third", Language.JAVA)
            }
            functions += throwingFunction("kotlinFirst", Language.KOTLIN)
            functions += throwingFunction("javaFirst", Language.JAVA)
            functions += throwingFunction("kotlinUnit", Language.KOTLIN, IrUnit)
            functions += throwingFunction("javaVoid", Language.JAVA, IrUnit)
        }
        val feature = buildModule {
            name = "feature"
            dependencies += library
            functions += throwingFunction("kotlinSecond", Language.KOTLIN)
            functions += throwingFunction("javaSecond", Language.JAVA)
            classes += buildClassDeclaration {
                name = "JavaVoidOwner"
                language = Language.JAVA
                classKind = ClassKind.FINAL
                functions += throwingFunction("memberVoid", Language.JAVA, IrUnit)
            }
        }
        val generatedMain = buildModule {
            name = "main"
            dependencies += feature
        }
        val program = buildProgram {
            modules += library
            modules += feature
            modules += generatedMain
        }
        var source = IrProgramPrinter().printToSingle(program)

        assertTrue(source.contains("KotlinOwnerReceiver.first()"))
        assertTrue(source.contains("KotlinOwnerReceiver.second()"))
        assertTrue(source.contains("KotlinOwnerReceiver.nullableOverload((null as NullableA?))"))
        assertTrue(source.contains("KotlinOwnerReceiver.nullableOverload((null as NullableB?))"))
        assertTrue(source.contains("JavaOwnerReceiver.third()"))
        assertTrue(source.contains("kotlinFirst()"))
        assertTrue(source.contains("kotlinSecond()"))
        assertTrue(source.contains("kotlinUnit(Unit)"))
        assertTrue(source.contains("JavaTopLevelContainer_library_0.javaFirst()"))
        assertTrue(source.contains("JavaTopLevelContainer_feature_1.javaSecond()"))
        assertFalse(source.contains("JavaTopLevelContainer_library_0.javaVoid("))
        assertFalse(source.contains("JavaVoidOwnerReceiver.memberVoid("))
        assertEquals(10, source.windowed("catch (_: UnsupportedOperationException)".length)
            .count { it == "catch (_: UnsupportedOperationException)" })
        assertFalse(source.contains("catch (_: RuntimeException)"))
        assertFalse(source.contains("catch (_: Exception)"))
        assertFalse(source.contains("catch (_: Throwable)"))
        assertFalse(source.contains("catch (_: Error)"))

        source = addExecutionTracker(source, listOf(
            "first", "second", "nullableA", "nullableB", "third",
            "kotlinFirst", "javaFirst", "kotlinUnit", "kotlinSecond", "javaSecond",
        ))
        source = instrumentKotlin(source, "first", "first")
        source = instrumentKotlin(source, "second", "second")
        source = instrumentKotlin(source, "nullableOverload", "nullableA", occurrence = 0)
        source = instrumentKotlin(source, "nullableOverload", "nullableB", occurrence = 1)
        source = instrumentJava(source, "third", "third")
        source = instrumentKotlin(source, "kotlinFirst", "kotlinFirst")
        source = instrumentJava(source, "javaFirst", "javaFirst")
        source = instrumentKotlin(source, "kotlinUnit", "kotlinUnit")
        source = instrumentKotlin(source, "kotlinSecond", "kotlinSecond")
        source = instrumentJava(source, "javaSecond", "javaSecond")

        source = addBoxChecks(
            source,
            "if (!ExecutionTracker.first || !ExecutionTracker.second ||\n" +
                "    !ExecutionTracker.nullableA || !ExecutionTracker.nullableB ||\n" +
                "    !ExecutionTracker.third || !ExecutionTracker.kotlinFirst ||\n" +
                "    !ExecutionTracker.javaFirst || !ExecutionTracker.kotlinUnit ||\n" +
                "    !ExecutionTracker.kotlinSecond || !ExecutionTracker.javaSecond) " +
                "return \"MISSING_CALL\"",
        )

        val result = CrossLangFuzzerKotlinRunner.K2Jdk17Compiler.testProgram(source)

        assertTrue(result.success, result.toString())
    }

    @Test
    fun `K2 compiles single module with existing default auxiliary names`() {
        val program = buildProgram {
            modules += buildModule {
                name = "single"
                classes += buildClassDeclaration {
                    name = "JavaTopLevelContainer"
                    language = Language.JAVA
                    classKind = ClassKind.FINAL
                }
                classes += buildClassDeclaration {
                    name = "KotlinTopLevel"
                    language = Language.KOTLIN
                    classKind = ClassKind.FINAL
                }
                classes += buildClassDeclaration {
                    name = "KotlinTopLevelKt"
                    language = Language.KOTLIN
                    classKind = ClassKind.FINAL
                }
                functions += throwingFunction("javaTop", Language.JAVA)
                functions += throwingFunction("kotlinTop", Language.KOTLIN)
            }
        }

        val source = IrProgramPrinter().printToSingle(program)

        assertTrue(source.contains("// FILE: JavaTopLevelContainer_1.java"))
        assertTrue(source.contains("JavaTopLevelContainer_1.javaTop()"))
        assertTrue(source.contains("// FILE: KotlinTopLevel_1.kt"))

        val result = CrossLangFuzzerKotlinRunner.K2Jdk17Compiler.testProgram(source)

        assertTrue(result.success, result.toString())
    }

    @Test
    fun `K2 compiles duplicate module names and conflicting auxiliary names`() {
        val first = buildModule {
            name = "same"
            classes += buildClassDeclaration {
                name = "JavaTopLevelContainer_same_0"
                language = Language.JAVA
                classKind = ClassKind.FINAL
            }
            classes += buildClassDeclaration {
                name = "KotlinTopLevel_same_0Kt"
                language = Language.KOTLIN
                classKind = ClassKind.FINAL
            }
            functions += throwingFunction("javaFirst", Language.JAVA)
            functions += throwingFunction("kotlinFirst", Language.KOTLIN)
        }
        val second = buildModule {
            name = "same"
            dependencies += first
            classes += buildClassDeclaration {
                name = "JavaTopLevelContainer_same_1_1"
                language = Language.JAVA
                classKind = ClassKind.FINAL
            }
            classes += buildClassDeclaration {
                name = "KotlinTopLevel_same_1_1Kt"
                language = Language.KOTLIN
                classKind = ClassKind.FINAL
            }
            functions += throwingFunction("javaSecond", Language.JAVA)
            functions += throwingFunction("kotlinSecond", Language.KOTLIN)
        }
        val program = buildProgram {
            modules += first
            modules += second
        }

        val source = IrProgramPrinter().printToSingle(program)

        assertTrue(source.contains("// MODULE: same(annotations)"))
        assertTrue(source.contains("// MODULE: same_1(annotations, same)"))
        assertTrue(source.contains("// FILE: JavaTopLevelContainer_same_0_1.java"))
        assertTrue(source.contains("// FILE: KotlinTopLevel_same_0_1.kt"))
        assertTrue(source.contains("// FILE: JavaTopLevelContainer_same_1_1_1.java"))
        assertTrue(source.contains("// FILE: KotlinTopLevel_same_1_1_1.kt"))

        val result = CrossLangFuzzerKotlinRunner.K2Jdk17Compiler.testProgram(source)

        assertTrue(result.success, result.toString())
    }

    @Test
    fun `K2 compiles Kotlin class main and trivial harness in different files`() {
        val program = buildProgram {
            modules += buildModule {
                name = "generated"
                classes += buildClassDeclaration {
                    name = "main"
                    language = Language.KOTLIN
                    classKind = ClassKind.FINAL
                }
            }
        }

        val source = IrProgramPrinter().printToSingle(program)

        assertTrue(source.contains("// FILE: main.kt"))
        assertTrue(source.contains("class main"))
        assertTrue(source.contains("// FILE: main_1.kt"))
        assertTrue(source.contains("fun box(): String"))

        val result = CrossLangFuzzerKotlinRunner.K2Jdk17Compiler.testProgram(source)

        assertTrue(result.success, result.toString())
    }

    private fun addExecutionTracker(source: String, flags: List<String>): String {
        val tracker = buildString {
            append("// FILE: ExecutionTracker.java\n")
            append("public final class ExecutionTracker {\n")
            flags.forEach { append("    public static boolean $it;\n") }
            append("}\n")
        }
        val firstAnnotationFile = "// FILE: org/jetbrains/annotations/NotNull.java"
        require(firstAnnotationFile in source)
        return source.replaceFirst(firstAnnotationFile, tracker + firstAnnotationFile)
    }

    private fun instrumentKotlin(
        source: String,
        functionName: String,
        flag: String,
        occurrence: Int = 0,
    ): String {
        val declaration = Regex(
            "fun\\s*(?:<[^>]+>\\s*)?${Regex.escape(functionName)}\\s*\\([^)]*\\)[^{\\n]*\\{",
        )
        val matches = declaration.findAll(source).toList()
        require(matches.size > occurrence) { "Missing Kotlin function body: $functionName #$occurrence" }
        val bodyStart = matches[occurrence].range.last + 1
        return source.replaceRange(bodyStart, bodyStart, "\n        ExecutionTracker.$flag = true")
    }

    private fun instrumentJava(source: String, functionName: String, flag: String): String {
        val declaration = Regex("\\b$functionName\\s*\\([^)]*\\)[^{;]*\\{")
        val match = declaration.find(source)
            ?: throw IllegalArgumentException("Missing Java function body: $functionName")
        val bodyStart = match.range.last + 1
        return source.replaceRange(bodyStart, bodyStart, "\n        ExecutionTracker.$flag = true;")
    }

    private fun addBoxChecks(source: String, checks: String): String {
        val returnOk = Regex("(?m)^(\\s*)return \\\"OK\\\"$")
        val match = returnOk.find(source)
            ?: throw IllegalArgumentException("Missing box return")
        val indent = match.groupValues[1]
        val indentedChecks = checks.lines().joinToString("\n") { indent + it.trimStart() }
        return source.replaceRange(match.range, indentedChecks + "\n" + indent + "return \"OK\"")
    }

    private fun throwingFunction(name: String, language: Language, vararg parameterTypes: IrType) = buildFunctionDeclaration {
        this.name = name
        this.language = language
        isFinal = true
        body = buildBlock()
        parameterList = buildParameterList {
            parameterTypes.forEachIndexed { index, parameterType ->
                parameters += buildParameter {
                    this.name = "p$index"
                    type = parameterType
                }
            }
        }
    }
}
