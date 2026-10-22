package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.NullabilityAnnotationProvider
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JSpecifyPrinterCompilerTest {
    @Test
    fun `K2 accepts generated JSpecify sources`() {
        val javaClass = buildClassDeclaration {
            name = "JavaClass"
            language = Language.JAVA
            classKind = ClassKind.FINAL
        }
        val program = buildProgram {
            classes += javaClass
            nullabilityAnnotationProvider = NullabilityAnnotationProvider.JSPECIFY
        }
        val source = IrProgramPrinter().printToSingle(program)

        val result = CrossLangFuzzerKotlinRunner.K2Jdk17Compiler.testProgram(source)

        assertTrue(result.success, result.toString())
    }
}
