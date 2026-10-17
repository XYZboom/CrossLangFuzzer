package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IrProgramPrinterBytecodeTest {
    @Test
    fun `printToSingle outputs module directives and handles single module`() {
        val classA = buildClassDeclaration {
            name = "ClassA"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += classA
            }
        }
        val output = IrProgramPrinter().printToSingle(program)
        assertTrue(output.contains("// MODULE: main"))
        assertTrue(output.contains("// FILE: ClassA.kt"))
        assertTrue(output.contains("// FILE: main.kt"))
    }

    @Test
    fun `printToSingle outputs multiple modules with dependencies and mixed languages`() {
        val classJava = buildClassDeclaration {
            name = "JavaClass"
            language = Language.JAVA
            classKind = ClassKind.FINAL
        }
        val classKt = buildClassDeclaration {
            name = "KtClass"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val m0 = buildModule {
            name = "m0"
            classes += classJava
        }
        val main = buildModule {
            name = "main"
            dependencies += m0
            classes += classKt
        }
        val program = buildProgram {
            modules += m0
            modules += main
        }
        val output = IrProgramPrinter().printToSingle(program)
        assertTrue(output.contains("// MODULE: m0"))
        assertTrue(output.contains("// FILE: JavaClass.java"))
        assertTrue(output.contains("// MODULE: main(m0)"))
        assertTrue(output.contains("// FILE: KtClass.kt"))
    }
}
