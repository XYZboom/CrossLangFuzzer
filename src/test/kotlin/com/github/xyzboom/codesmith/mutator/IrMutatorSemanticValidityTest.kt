package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.type
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

class IrMutatorSemanticValidityTest {
    private val alwaysFalseRandom = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    @Test
    fun `relaxing a type parameter upper bound is valid`() {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val target = buildClassDeclaration {
            name = "Target"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            typeParameters += typeParameter
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += target
            }
        }
        val mutator = IrMutator(
            config = MutatorConfig.allZero.copy(
                mutateClassTypeParameterUpperBoundNullabilityWeight = 1,
            ),
            generator = IrDeclGenerator(random = Random(0)),
        )

        mutator.mutate(program) shouldBe SemanticValidity.VALID
        mutator.mutate(program = buildProgram {
            modules += buildModule { name = "main" }
        }) shouldBe null
    }

    @Test
    fun `tightening a type parameter upper bound rejects a nullable argument`() {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = buildNullableType { innerType = IrAny }
        }
        val target = buildClassDeclaration {
            name = "Target"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            typeParameters += typeParameter
        }
        val targetOfNullableAny = target.type as IrParameterizedClassifier
        targetOfNullableAny.putTypeArgument(
            typeParameter,
            buildNullableType { innerType = IrAny },
        )
        val user = buildClassDeclaration {
            name = "User"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            implementedTypes += targetOfNullableAny
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += target
                classes += user
            }
        }
        val mutator = IrMutator(
            config = MutatorConfig.allZero.copy(
                mutateClassTypeParameterUpperBoundNullabilityWeight = 1,
            ),
            generator = IrDeclGenerator(random = Random(0)),
        )

        mutator.mutate(program) shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `changing a non-override parameter nullability is valid`() {
        val function = buildFunctionDeclaration {
            name = "f"
            language = Language.KOTLIN
            returnType = IrAny
            body = buildBlock()
            parameterList = buildParameterList {
                parameters += buildParameter {
                    name = "value"
                    type = IrAny
                }
            }
        }
        val clazz = buildClassDeclaration {
            name = "C"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += function
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += clazz
            }
        }

        parameterNullabilityMutator().mutate(program) shouldBe SemanticValidity.VALID
    }

    @Test
    fun `changing a Kotlin override parameter nullability is invalid`() {
        val parentFunction = parameterFunction(Language.KOTLIN)
        val childFunction = parameterFunction(Language.KOTLIN).apply {
            isOverride = true
            override += parentFunction
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += childFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += child
            }
        }

        parameterNullabilityMutator().mutate(program) shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `changing an override of an unannotated Java parameter is valid`() {
        val parentFunction = parameterFunction(Language.JAVA).apply {
            printNullableAnnotations = false
        }
        val childFunction = parameterFunction(Language.KOTLIN).apply {
            isOverride = true
            override += parentFunction
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += childFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += child
            }
        }

        parameterNullabilityMutator().mutate(program) shouldBe SemanticValidity.VALID
    }

    @Test
    fun `changing a parameter invalidates a downstream Kotlin override`() {
        val parentFunction = parameterFunction(Language.KOTLIN)
        val childFunction = parameterFunction(Language.KOTLIN).apply {
            isOverride = true
            override += parentFunction
        }
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            functions += parentFunction
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += childFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                // With alwaysFalseRandom, shuffling these two classes visits
                // Parent first, so the declaration being overridden is mutated.
                classes += child
                classes += parent
            }
        }

        parameterNullabilityMutator().mutate(program) shouldBe SemanticValidity.INVALID
    }

    private fun parameterNullabilityMutator() = IrMutator(
        config = MutatorConfig.allZero.copy(mutateParameterNullabilityWeight = 1),
        generator = IrDeclGenerator(random = alwaysFalseRandom),
    )

    private fun parameterFunction(language: Language) = buildFunctionDeclaration {
        name = "f"
        this.language = language
        returnType = IrAny
        body = buildBlock()
        parameterList = buildParameterList {
            parameters += buildParameter {
                name = "value"
                type = IrAny
            }
        }
    }
}
