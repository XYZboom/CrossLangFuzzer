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
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildPlatformType
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
    fun `changing an override of a Java platform parameter is valid`() {
        val parentFunction = parameterFunction(
            Language.JAVA,
            buildPlatformType { innerType = IrAny },
        )
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

        val mutator = parameterNullabilityMutator()
        mutator.mutateParameterNullability(program) shouldBe true
        mutator.semanticValidity shouldBe SemanticValidity.VALID
    }

    @Test
    fun `legacy Java annotation flag does not make an explicit parameter a platform type`() {
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

        val mutator = parameterNullabilityMutator()
        mutator.mutateParameterNullability(program) shouldBe true
        mutator.semanticValidity shouldBe SemanticValidity.INVALID
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

    @Test
    fun `changing a generic argument in an override parameter is invalid`() {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val wrapper = buildClassDeclaration {
            name = "Wrapper"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            typeParameters += typeParameter
        }
        val oldArgument = buildClassDeclaration {
            name = "OldArgument"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        fun wrapperOfOldArgument(): IrParameterizedClassifier {
            return (wrapper.type as IrParameterizedClassifier).apply {
                putTypeArgument(typeParameter, oldArgument.type)
            }
        }

        val parentFunction = parameterFunction(Language.KOTLIN, wrapperOfOldArgument())
        val childFunction = parameterFunction(Language.KOTLIN, wrapperOfOldArgument()).apply {
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
        val mutator = IrMutator(
            config = MutatorConfig.allZero.copy(
                mutateGenericArgumentInMemberFunctionParameterWeight = 1,
            ),
            generator = IrDeclGenerator(random = alwaysFalseRandom),
        )

        mutator.mutate(program) shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `changing an unused parent generic argument is valid`() {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            typeParameters += typeParameter
        }
        val oldArgument = buildClassDeclaration {
            name = "OldArgument"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val parentType = parent.type as IrParameterizedClassifier
        parentType.putTypeArgument(typeParameter, oldArgument.type)
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            implementedTypes += parentType
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += child
            }
        }

        parentGenericArgumentMutator().mutate(program) shouldBe SemanticValidity.VALID
    }

    @Test
    fun `changing a parent generic argument can violate its upper bound`() {
        val bound = buildClassDeclaration {
            name = "Bound"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
        }
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = bound.type
        }
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            typeParameters += typeParameter
        }
        val parentType = parent.type as IrParameterizedClassifier
        parentType.putTypeArgument(typeParameter, bound.type)
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            implementedTypes += parentType
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += child
            }
        }

        parentGenericArgumentMutator().mutate(program) shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `changing a parent argument used by a member of concrete class is invalid`() {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            typeParameters += typeParameter
            functions += parameterFunction(Language.KOTLIN, typeParameter)
        }
        val oldArgument = buildClassDeclaration {
            name = "OldArgument"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val parentType = parent.type as IrParameterizedClassifier
        parentType.putTypeArgument(typeParameter, oldArgument.type)
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            implementedTypes += parentType
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += child
            }
        }

        parentGenericArgumentMutator().mutate(program) shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `changing a parent argument used by a member of an interface without overrides is valid`() {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            typeParameters += typeParameter
            functions += parameterFunction(Language.KOTLIN, typeParameter)
        }
        val oldArgument = buildClassDeclaration {
            name = "OldArgument"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val parentType = parent.type as IrParameterizedClassifier
        parentType.putTypeArgument(typeParameter, oldArgument.type)
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            implementedTypes += parentType
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += child
            }
        }

        parentGenericArgumentMutator().mutate(program) shouldBe SemanticValidity.VALID
    }

    @Test
    fun `removing an override required for an abstract interface member is invalid`() {
        val parentFunction = parameterFunction(Language.KOTLIN).apply { body = null }
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            functions += parentFunction
        }
        val childFunction = parameterFunction(Language.KOTLIN).apply {
            isOverride = true
            override += parentFunction
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            implementedTypes += parent.type
            functions += childFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += parent
                classes += child
            }
        }

        removeOverrideMutator().mutate(program) shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `removing an abstract override needed by a concrete descendant is invalid`() {
        val parentFunction = parameterFunction(Language.KOTLIN).apply { body = null }
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            functions += parentFunction
        }
        val middleFunction = parameterFunction(Language.KOTLIN).apply {
            body = null
            isOverride = true
            override += parentFunction
        }
        val middle = buildClassDeclaration {
            name = "Middle"
            language = Language.KOTLIN
            classKind = ClassKind.ABSTRACT
            implementedTypes += parent.type
            functions += middleFunction
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            superType = middle.type
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += parent
                classes += middle
                classes += child
            }
        }

        removeOverrideMutator().mutate(program) shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `removing an optional override of a concrete parent member is valid`() {
        val parentFunction = parameterFunction(Language.KOTLIN)
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            functions += parentFunction
        }
        val childFunction = parameterFunction(Language.KOTLIN).apply {
            isOverride = true
            override += parentFunction
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            superType = parent.type
            functions += childFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += parent
                classes += child
            }
        }

        removeOverrideMutator().mutate(program) shouldBe SemanticValidity.VALID
    }

    @Test
    fun `changing a class type parameter upper bound without uses is valid`() {
        val bound = buildClassDeclaration {
            name = "Bound"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
        }
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val target = buildClassDeclaration {
            name = "Target"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            typeParameters += typeParameter
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += bound
                classes += target
            }
        }

        upperBoundMutator().mutate(program) shouldBe SemanticValidity.VALID
    }

    @Test
    fun `changing a class type parameter upper bound rejects an incompatible argument`() {
        val bound = buildClassDeclaration {
            name = "Bound"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
        }
        val argument = buildClassDeclaration {
            name = "Argument"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val target = buildClassDeclaration {
            name = "Target"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            typeParameters += typeParameter
        }
        val targetType = target.type as IrParameterizedClassifier
        targetType.putTypeArgument(typeParameter, argument.type)
        val user = buildClassDeclaration {
            name = "User"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            implementedTypes += targetType
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += bound
                classes += argument
                classes += target
                classes += user
            }
        }

        upperBoundMutator().mutate(program) shouldBe SemanticValidity.INVALID
    }

    private fun upperBoundMutator() = IrMutator(
        config = MutatorConfig.allZero.copy(mutateClassTypeParameterUpperBoundWeight = 1),
        generator = IrDeclGenerator(random = alwaysFalseRandom),
    )

    @Test
    fun `changing a non-override return type nullability is valid`() {
        val function = returnTypeFunction(Language.KOTLIN, IrAny)
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

        returnTypeNullabilityMutator().mutate(program) shouldBe SemanticValidity.VALID
    }

    @Test
    fun `loosening a Kotlin override return type nullability to nullable when parent returns non-null is invalid`() {
        val parentFunction = returnTypeFunction(Language.KOTLIN, IrAny)
        val childFunction = returnTypeFunction(Language.KOTLIN, IrAny).apply {
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
            superType = parent.type
            functions += childFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += parent
                classes += child
            }
        }

        returnTypeNullabilityMutator().mutate(program) shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `tightening a Kotlin override return type nullability to non-null when parent returns nullable is valid`() {
        val nullableAny = buildNullableType { innerType = IrAny }
        val parentFunction = returnTypeFunction(Language.KOTLIN, nullableAny)
        val childFunction = returnTypeFunction(Language.KOTLIN, nullableAny).apply {
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
            superType = parent.type
            functions += childFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += parent
                classes += child
            }
        }

        returnTypeNullabilityMutator().mutate(program) shouldBe SemanticValidity.VALID
    }

    @Test
    fun `tightening a parent return type nullability to non-null breaks a downstream override returning nullable`() {
        val nullableAny = buildNullableType { innerType = IrAny }
        val parentFunction = returnTypeFunction(Language.KOTLIN, nullableAny)
        val childFunction = returnTypeFunction(Language.KOTLIN, nullableAny).apply {
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
            superType = parent.type
            functions += childFunction
        }
        // Mutate parent function first
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += child
                classes += parent
            }
        }

        returnTypeNullabilityMutator().mutate(program) shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `changing an override of a Java platform return type is valid`() {
        val parentFunction = returnTypeFunction(
            Language.JAVA,
            buildPlatformType { innerType = IrAny },
        )
        val childFunction = returnTypeFunction(Language.KOTLIN, IrAny).apply {
            isOverride = true
            override += parentFunction
        }
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.JAVA
            classKind = ClassKind.OPEN
            functions += parentFunction
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            superType = parent.type
            functions += childFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += parent
                classes += child
            }
        }

        val mutator = returnTypeNullabilityMutator()
        mutator.mutateReturnTypeNullability(program) shouldBe true
        mutator.semanticValidity shouldBe SemanticValidity.VALID
    }

    @Test
    fun `legacy Java annotation flag does not make an explicit return type a platform type`() {
        val parentFunction = returnTypeFunction(Language.JAVA, IrAny).apply {
            printNullableAnnotations = false
        }
        val childFunction = returnTypeFunction(Language.KOTLIN, IrAny).apply {
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

        val mutator = returnTypeNullabilityMutator()
        mutator.mutateReturnTypeNullability(program) shouldBe true
        mutator.semanticValidity shouldBe SemanticValidity.INVALID
    }

    private fun removeOverrideMutator() = IrMutator(
        config = MutatorConfig.allZero.copy(removeOverrideMemberFunctionWeight = 1),
        generator = IrDeclGenerator(random = alwaysFalseRandom),
    )

    private fun parentGenericArgumentMutator() = IrMutator(
        config = MutatorConfig.allZero.copy(mutateGenericArgumentInParentWeight = 1),
        generator = IrDeclGenerator(random = alwaysFalseRandom),
    )

    private fun parameterNullabilityMutator() = IrMutator(
        config = MutatorConfig.allZero.copy(mutateParameterNullabilityWeight = 1),
        generator = IrDeclGenerator(random = alwaysFalseRandom),
    )

    private fun returnTypeNullabilityMutator() = IrMutator(
        config = MutatorConfig.allZero.copy(mutateReturnTypeNullabilityWeight = 1),
        generator = IrDeclGenerator(random = alwaysFalseRandom),
    )

    private fun parameterFunction(language: Language, parameterType: IrType = IrAny) = buildFunctionDeclaration {
        name = "f"
        this.language = language
        returnType = IrAny
        body = buildBlock()
        parameterList = buildParameterList {
            parameters += buildParameter {
                name = "value"
                type = parameterType
            }
        }
    }

    private fun returnTypeFunction(language: Language, returnType: IrType = IrAny) = buildFunctionDeclaration {
        name = "f"
        this.language = language
        this.returnType = returnType
        body = buildBlock()
        parameterList = buildParameterList()
    }
}
