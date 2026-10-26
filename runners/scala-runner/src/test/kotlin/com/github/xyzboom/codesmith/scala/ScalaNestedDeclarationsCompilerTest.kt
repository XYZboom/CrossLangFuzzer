package com.github.xyzboom.codesmith.scala

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.Visibility
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScalaNestedDeclarationsCompilerTest {
    @Test
    fun `Scala 3 accepts nested declarations with source visibility`() {
        val nested = buildClassDeclaration {
            name = "Nested"
            language = Language.SCALA
            visibility = Visibility.PRIVATE
            classKind = ClassKind.FINAL
        }
        val outer = buildClassDeclaration {
            name = "Outer"
            language = Language.SCALA
            visibility = Visibility.PUBLIC
            classKind = ClassKind.OPEN
            classes += nested
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += outer
            }
        }

        val result = CrossLangFuzzerScalaRunner.Scala3Compiler.compile(program)

        assertTrue(result.success, result.toString())
    }
}
