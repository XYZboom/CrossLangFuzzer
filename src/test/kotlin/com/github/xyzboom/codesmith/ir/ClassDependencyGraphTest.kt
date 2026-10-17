package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.type
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import java.time.Duration

class ClassDependencyGraphTest {
    @Test
    fun `dependency order is deterministic for wrapped parameterized parameter types`() {
        val consumer = classDeclaration("Consumer")
        val dependency = classDeclaration("Dependency")
        val typeParameter = buildTypeParameter { name = "T"; upperbound = IrAny }
        val wrapper = buildClassDeclaration {
            name = "Wrapper"
            classKind = ClassKind.FINAL
            language = Language.KOTLIN
            typeParameters += typeParameter
        }
        val wrapperType = wrapper.type as IrParameterizedClassifier
        wrapperType.putTypeArgument(typeParameter, dependency.type)
        consumer.functions += buildFunctionDeclaration {
            name = "consume"
            language = Language.KOTLIN
            returnType = IrAny
            parameterList = buildParameterList {
                parameters += buildParameter {
                    name = "value"
                    type = buildNullableType { innerType = wrapperType }
                }
            }
        }
        val independent = classDeclaration("Independent")
        val program = buildProgram {
            classes += consumer
            classes += dependency
            classes += wrapper
            classes += independent
        }

        val expected = listOf("Dependency", "Wrapper", "Consumer", "Independent")
        repeat(3) {
            assertEquals(expected, program.topologicalOrderedClasses.map { it.name }.toList())
        }
    }

    @Test
    fun `class type parameter bound contributes a dependency`() {
        val consumer = classDeclaration("Consumer")
        val dependency = classDeclaration("Dependency")
        consumer.typeParameters += buildTypeParameter {
            name = "T"
            upperbound = dependency.type
        }
        val program = buildProgram {
            classes += consumer
            classes += dependency
        }

        assertEquals(listOf("Dependency", "Consumer"), program.topologicalOrderedClasses.map { it.name }.toList())
    }

    @Test
    fun `cyclic type parameter bounds terminate`() {
        val first = buildTypeParameter { name = "First"; upperbound = IrAny }
        val second = buildTypeParameter { name = "Second"; upperbound = first }
        first.upperbound = second
        val owner = classDeclaration("Owner").apply {
            typeParameters += first
            typeParameters += second
        }
        val program = buildProgram { classes += owner }

        assertTimeoutPreemptively(Duration.ofSeconds(1)) {
            assertEquals(listOf("Owner"), program.topologicalOrderedClasses.map { it.name }.toList())
        }
    }

    private fun classDeclaration(name: String) = buildClassDeclaration {
        this.name = name
        classKind = ClassKind.FINAL
        language = Language.KOTLIN
    }
}
