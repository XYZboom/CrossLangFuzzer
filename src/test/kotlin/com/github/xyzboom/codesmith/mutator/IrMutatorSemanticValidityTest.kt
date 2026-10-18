package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
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
}
