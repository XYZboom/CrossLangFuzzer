package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.NullabilityAnnotationProvider
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JSpecifyPrinterTest {
    @Test
    fun `JSpecify provider prints matching imports annotations and stubs`() {
        val javaClass = buildClassDeclaration {
            name = "JavaClass"
            language = Language.JAVA
            classKind = ClassKind.FINAL
        }
        javaClass.functions += javaFunction(javaClass.name)
        val program = buildProgram {
            classes += javaClass
            nullabilityAnnotationProvider = NullabilityAnnotationProvider.JSPECIFY
        }

        val output = IrProgramPrinter().printToSingle(program)
        val annotationFiles = IrProgramPrinter().print(program).keys
            .filter { it.startsWith("org/") && "/annotations/" in it }
            .toSet()

        assertEquals(
            setOf(
                "org/jspecify/annotations/NonNull.java",
                "org/jspecify/annotations/Nullable.java",
            ),
            annotationFiles,
        )
        assertEquals(
            2,
            output.lines().count { it.startsWith("// FILE: org/jspecify/annotations/") },
        )
        assertTrue(output.contains("import org.jspecify.annotations.NonNull;"))
        assertTrue(output.contains("import org.jspecify.annotations.Nullable;"))
        assertTrue(output.contains("// FILE: org/jspecify/annotations/NonNull.java"))
        assertTrue(output.contains("// FILE: org/jspecify/annotations/Nullable.java"))
        assertTrue(output.contains("@NonNull Object value"))
        assertFalse(output.contains("org.jetbrains.annotations.NotNull"))
        assertFalse(output.contains("org/jetbrains/annotations/NotNull.java"))
    }

    @Test
    fun `JetBrains provider remains the printer default`() {
        val javaClass = buildClassDeclaration {
            name = "JavaClass"
            language = Language.JAVA
            classKind = ClassKind.FINAL
        }
        javaClass.functions += javaFunction(javaClass.name)
        val program = buildProgram {
            classes += javaClass
        }

        val output = IrProgramPrinter().printToSingle(program)
        val annotationFiles = IrProgramPrinter().print(program).keys
            .filter { it.startsWith("org/") && "/annotations/" in it }
            .toSet()

        assertEquals(
            setOf(
                "org/jetbrains/annotations/NotNull.java",
                "org/jetbrains/annotations/Nullable.java",
            ),
            annotationFiles,
        )
        assertEquals(
            2,
            output.lines().count { it.startsWith("// FILE: org/jetbrains/annotations/") },
        )
        assertTrue(output.contains("import org.jetbrains.annotations.NotNull;"))
        assertTrue(output.contains("// FILE: org/jetbrains/annotations/NotNull.java"))
        assertTrue(output.contains("@NotNull Object value"))
        assertFalse(output.contains("org.jspecify.annotations.NonNull"))
        assertFalse(output.contains("org/jspecify/annotations/NonNull.java"))
    }

    private fun javaFunction(className: String) = buildFunctionDeclaration {
        name = "consume"
        language = Language.JAVA
        containingClassName = className
        body = buildBlock()
        parameterList = buildParameterList {
            parameters += buildParameter {
                name = "value"
                type = IrAny
            }
        }
    }
}
