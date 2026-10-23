package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Real-K2 execution proof for the complete inherited receiver matrix. */
class CallMatrixCompilerTest {
    /** Exercises parent-instance, child-instance, and child-as-parent static receiver shapes. */
    @Test
    fun `concrete Kotlin parent and child execute all three parent receiver shapes`() {
        val parent = clazz("MatrixParent", Language.KOTLIN, ClassKind.OPEN, "parentFun")
        val child = clazz("MatrixChild", Language.KOTLIN, ClassKind.FINAL, "childFun").apply {
            superType = buildSimpleClassifier { classDecl = parent }
        }
        var source = print(parent, child)

        assertTrue(source.contains("MatrixParentReceiver.parentFun()"), source)
        assertTrue(source.contains("MatrixChildReceiver.parentFun()"), source)
        assertTrue(source.contains("val MatrixChildAsMatrixParentReceiver: MatrixParent = MatrixChildReceiver"), source)
        assertTrue(source.contains("MatrixChildAsMatrixParentReceiver.parentFun()"), source)

        source = tracked(source, mapOf("parentFun" to 3, "childFun" to 1))
        source = instrumentKotlin(source, "parentFun", "parentFun")
        source = instrumentKotlin(source, "childFun", "childFun")
        assertCompiles(source)
    }

    @Test
    fun `Java parent and Kotlin child execute all three parent receiver shapes`() {
        val parent = clazz("MatrixJavaParent", Language.JAVA, ClassKind.OPEN, "javaParentFun")
        val child = clazz("MatrixKotlinChild", Language.KOTLIN, ClassKind.FINAL, "kotlinChildFun").apply {
            superType = buildSimpleClassifier { classDecl = parent }
        }
        var source = print(parent, child)

        assertTrue(source.contains("MatrixJavaParentReceiver.javaParentFun()"), source)
        assertTrue(source.contains("MatrixKotlinChildReceiver.javaParentFun()"), source)
        assertTrue(
            source.contains("val MatrixKotlinChildAsMatrixJavaParentReceiver: MatrixJavaParent = MatrixKotlinChildReceiver"),
            source,
        )
        assertTrue(source.contains("MatrixKotlinChildAsMatrixJavaParentReceiver.javaParentFun()"), source)

        source = tracked(source, mapOf("javaParentFun" to 3, "kotlinChildFun" to 1))
        source = instrumentJava(source, "javaParentFun", "javaParentFun")
        source = instrumentKotlin(source, "kotlinChildFun", "kotlinChildFun")
        assertCompiles(source)
    }

    @Test
    fun `three-level inheritance executes transitive receiver shapes without duplicates`() {
        val grand = clazz("MatrixGrand", Language.KOTLIN, ClassKind.OPEN, "grandFun")
        val parent = clazz("MatrixMiddle", Language.KOTLIN, ClassKind.OPEN, "middleFun").apply {
            superType = buildSimpleClassifier { classDecl = grand }
        }
        val child = clazz("MatrixLeaf", Language.KOTLIN, ClassKind.FINAL, "leafFun").apply {
            superType = buildSimpleClassifier { classDecl = parent }
        }
        var source = print(grand, parent, child)

        assertTrue(source.contains("MatrixLeafReceiver.grandFun()"), source)
        assertTrue(source.contains("val MatrixLeafAsMatrixGrandReceiver: MatrixGrand = MatrixLeafReceiver"), source)
        assertTrue(source.contains("MatrixLeafAsMatrixGrandReceiver.grandFun()"), source)

        source = tracked(source, mapOf("grandFun" to 5, "middleFun" to 3, "leafFun" to 1))
        source = instrumentKotlin(source, "grandFun", "grandFun")
        source = instrumentKotlin(source, "middleFun", "middleFun")
        source = instrumentKotlin(source, "leafFun", "leafFun")
        assertCompiles(source)
    }

    @Test
    fun `generic parent static receiver keeps the actual inherited type argument`() {
        val typeParameter = buildTypeParameter {
            name = "T0"
            upperbound = IrAny
        }
        val parent = clazz("MatrixGenericParent", Language.KOTLIN, ClassKind.OPEN, "genericParentFun").apply {
            typeParameters += typeParameter
        }
        val actualParent = (parent.type as IrParameterizedClassifier).apply {
            putTypeArgument(typeParameter, IrAny)
        }
        val child = clazz("MatrixGenericChild", Language.KOTLIN, ClassKind.FINAL, "genericChildFun").apply {
            superType = actualParent
        }
        var source = print(parent, child)

        assertTrue(source.contains("val MatrixGenericParentReceiver = MatrixGenericParent<Any>()"), source)
        assertTrue(
            source.contains(
                "val MatrixGenericChildAsMatrixGenericParentReceiver: MatrixGenericParent<Any> = MatrixGenericChildReceiver",
            ),
            source,
        )
        assertTrue(source.contains("MatrixGenericChildAsMatrixGenericParentReceiver.genericParentFun()"), source)

        source = tracked(source, mapOf("genericParentFun" to 3, "genericChildFun" to 1))
        source = instrumentKotlin(source, "genericParentFun", "genericParentFun")
        source = instrumentKotlin(source, "genericChildFun", "genericChildFun")
        assertCompiles(source)
    }

    @Test
    fun `abstract parent has child-direct and child-as-parent calls but no parent instance`() {
        val parent = clazz("MatrixAbstract", Language.KOTLIN, ClassKind.ABSTRACT, "abstractFun")
        val child = clazz("MatrixConcrete", Language.KOTLIN, ClassKind.FINAL, "concreteFun").apply {
            superType = buildSimpleClassifier { classDecl = parent }
        }
        var source = print(parent, child)

        assertFalse(source.contains("val MatrixAbstractReceiver ="), source)
        assertTrue(source.contains("MatrixConcreteReceiver.abstractFun()"), source)
        assertTrue(source.contains("val MatrixConcreteAsMatrixAbstractReceiver: MatrixAbstract = MatrixConcreteReceiver"), source)

        source = tracked(source, mapOf("abstractFun" to 2, "concreteFun" to 1))
        source = instrumentKotlin(source, "abstractFun", "abstractFun")
        source = instrumentKotlin(source, "concreteFun", "concreteFun")
        assertCompiles(source)
    }

    @Test
    fun `the matrix catches only UnsupportedOperationException`() {
        val owner = clazz("MatrixGuard", Language.KOTLIN, ClassKind.FINAL, "guardedFun")
        val source = print(owner)

        assertTrue(source.contains("catch (_: UnsupportedOperationException)"), source)
        assertFalse(source.contains("catch (_: RuntimeException)"), source)
        assertFalse(source.contains("catch (_: Exception)"), source)
        assertFalse(source.contains("catch (_: Throwable)"), source)
        assertFalse(source.contains("catch (_: Error)"), source)
    }

    private fun clazz(
        name: String,
        language: Language,
        kind: ClassKind,
        functionName: String,
    ) = buildClassDeclaration {
        this.name = name
        this.language = language
        classKind = kind
        functions += buildFunctionDeclaration {
            this.name = functionName
            this.language = language
            isFinal = true
            body = buildBlock()
            parameterList = buildParameterList { }
        }
    }

    private fun print(vararg classes: com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration): String =
        IrProgramPrinter().printToSingle(buildProgram {
            modules += buildModule {
                name = "main"
                this.classes += classes
            }
        })

    private fun tracked(source: String, expectedCounts: Map<String, Int>): String {
        var result = addExecutionTracker(source, expectedCounts.keys)
        val checks = expectedCounts.entries.joinToString("\n") { (counter, expected) ->
            "if (ExecutionTracker.$counter != $expected) return \"BAD_${counter.uppercase()}_\${ExecutionTracker.$counter}\""
        }
        result = addBoxChecks(result, checks)
        return result
    }

    private fun addExecutionTracker(source: String, counters: Set<String>): String {
        val tracker = buildString {
            append("// FILE: ExecutionTracker.java\n")
            append("public final class ExecutionTracker {\n")
            counters.forEach { append("    public static int $it;\n") }
            append("}\n")
        }
        val firstAnnotationFile = "// FILE: org/jetbrains/annotations/NotNull.java"
        require(firstAnnotationFile in source)
        return source.replaceFirst(firstAnnotationFile, tracker + firstAnnotationFile)
    }

    private fun instrumentKotlin(source: String, functionName: String, counter: String): String {
        val declaration = Regex(
            "fun\\s*(?:<[^>]+>\\s*)?${Regex.escape(functionName)}\\s*\\([^)]*\\)[^{\\n]*\\{",
        )
        val match = declaration.find(source)
            ?: throw IllegalArgumentException("Missing Kotlin function body: $functionName")
        val bodyStart = match.range.last + 1
        return source.replaceRange(bodyStart, bodyStart, "\n        ExecutionTracker.$counter++")
    }

    private fun instrumentJava(source: String, functionName: String, counter: String): String {
        val declaration = Regex("\\b${Regex.escape(functionName)}\\s*\\([^)]*\\)[^{;]*\\{")
        val match = declaration.find(source)
            ?: throw IllegalArgumentException("Missing Java function body: $functionName")
        val bodyStart = match.range.last + 1
        return source.replaceRange(bodyStart, bodyStart, "\n        ExecutionTracker.$counter++;")
    }

    private fun addBoxChecks(source: String, checks: String): String {
        val returnOk = Regex("(?m)^(\\s*)return \\\"OK\\\"$")
        val match = returnOk.find(source)
            ?: throw IllegalArgumentException("Missing box return")
        val indent = match.groupValues[1]
        val indentedChecks = checks.lines().joinToString("\n") { indent + it.trimStart() }
        return source.replaceRange(match.range, indentedChecks + "\n" + indent + "return \"OK\"")
    }

    private fun assertCompiles(source: String) {
        val result = CrossLangFuzzerKotlinRunner.K2Jdk17Compiler.testProgram(source)
        assertTrue(result.success, result.toString())
    }
}
