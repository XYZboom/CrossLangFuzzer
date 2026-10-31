package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeArgument
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.IrVariance
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildPlatformType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.getTypeArguments
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.validator.collectFunctionSignatureMap
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

        mutator.mutate(program)?.first shouldBe SemanticValidity.VALID
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

        mutator.mutate(program)?.first shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `tightening a type parameter upper bound accepts a star argument`() {
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
        val targetOfStar = target.type as IrParameterizedClassifier
        targetOfStar.putTypeArgument(typeParameter, IrTypeArgument.STAR)
        val user = buildClassDeclaration {
            name = "User"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            implementedTypes += targetOfStar
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

        mutator.mutate(program)?.first shouldBe SemanticValidity.VALID
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

        parameterNullabilityMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
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

        parameterNullabilityMutator().mutate(program)?.first shouldBe SemanticValidity.INVALID
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
        mutator.mutateParameterNullability(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `Java explicit parameter nullability is not a platform type`() {
        val parentFunction = parameterFunction(Language.JAVA)
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
        mutator.mutateParameterNullability(program)?.first shouldBe SemanticValidity.INVALID
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

        parameterNullabilityMutator().mutate(program)?.first shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `a Java redeclaration shields Kotlin descendants from an ancestor annotation change`() {
        val javaAncestorFunction = parameterFunction(Language.JAVA)
        val javaRedeclarationFunction = parameterFunction(Language.JAVA).apply {
            isOverride = true
            override += javaAncestorFunction
        }
        val kotlinChildFunction = parameterFunction(Language.KOTLIN).apply {
            isOverride = true
            override += javaRedeclarationFunction
        }
        val javaAncestor = buildClassDeclaration {
            name = "JavaAncestor"
            language = Language.JAVA
            classKind = ClassKind.OPEN
            functions += javaAncestorFunction
        }
        val javaRedeclaration = buildClassDeclaration {
            name = "JavaRedeclaration"
            language = Language.JAVA
            classKind = ClassKind.OPEN
            functions += javaRedeclarationFunction
        }
        val kotlinChild = buildClassDeclaration {
            name = "KotlinChild"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += kotlinChildFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                // With three classes, the deterministic shuffle visits the
                // declaration at index one first.
                classes += kotlinChild
                classes += javaAncestor
                classes += javaRedeclaration
            }
        }

        parameterNullabilityMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `a compatible Kotlin parent keeps a Java nullable conflict legal`() {
        val kotlinParentFunction = parameterFunction(Language.KOTLIN)
        val javaParentFunction = parameterFunction(Language.JAVA)
        val kotlinChildFunction = parameterFunction(Language.KOTLIN).apply {
            isOverride = true
            override += javaParentFunction
            override += kotlinParentFunction
        }
        val kotlinParent = buildClassDeclaration {
            name = "KotlinParent"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            functions += kotlinParentFunction
        }
        val javaParent = buildClassDeclaration {
            name = "JavaParent"
            language = Language.JAVA
            classKind = ClassKind.INTERFACE
            functions += javaParentFunction
        }
        val kotlinChild = buildClassDeclaration {
            name = "KotlinChild"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += kotlinChildFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                // With three classes, the deterministic shuffle visits the
                // declaration at index one first.
                classes += kotlinChild
                classes += javaParent
                classes += kotlinParent
            }
        }

        parameterNullabilityMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `an incompatible direct Java parent still invalidates a Kotlin override`() {
        val javaParentFunction = parameterFunction(Language.JAVA)
        val kotlinChildFunction = parameterFunction(Language.KOTLIN).apply {
            isOverride = true
            override += javaParentFunction
        }
        val javaParent = buildClassDeclaration {
            name = "JavaParent"
            language = Language.JAVA
            classKind = ClassKind.INTERFACE
            functions += javaParentFunction
        }
        val kotlinChild = buildClassDeclaration {
            name = "KotlinChild"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += kotlinChildFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += kotlinChild
                classes += javaParent
            }
        }

        parameterNullabilityMutator().mutate(program)?.first shouldBe SemanticValidity.INVALID
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

        mutator.mutate(program)?.first shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `generic argument mutation skips projected parameters and unwraps later nullable parameters`() {
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
        fun projectedWrapper(): IrParameterizedClassifier {
            return (wrapper.type as IrParameterizedClassifier).apply {
                putTypeArgument(typeParameter, IrTypeArgument.covariant(oldArgument.type))
            }
        }
        fun nullableInvariantWrapper(): IrType {
            val type = (wrapper.type as IrParameterizedClassifier).apply {
                putTypeArgument(typeParameter, oldArgument.type)
            }
            return buildNullableType { innerType = type }
        }
        fun overrideFunction() = buildFunctionDeclaration {
            name = "f"
            language = Language.KOTLIN
            returnType = IrAny
            body = buildBlock()
            parameterList = buildParameterList {
                parameters += buildParameter {
                    name = "projected"
                    type = projectedWrapper()
                }
                parameters += buildParameter {
                    name = "nullableInvariant"
                    type = nullableInvariantWrapper()
                }
            }
        }
        val parentFunction = overrideFunction()
        val childFunction = overrideFunction().apply {
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

        mutator.mutate(program)?.first shouldBe SemanticValidity.INVALID
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

        parentGenericArgumentMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `changing an unused generic superclass argument is valid`() {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
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
            superType = parentType
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += child
            }
        }

        parentGenericArgumentMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `parent generic argument mutation skips projected arguments`() {
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
        val projectedArguments = listOf(
            IrTypeArgument.covariant(oldArgument.type),
            IrTypeArgument.contravariant(oldArgument.type),
            IrTypeArgument.STAR,
        )

        for (projectedArgument in projectedArguments) {
            val parentType = parent.type as IrParameterizedClassifier
            parentType.putTypeArgument(typeParameter, projectedArgument)
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

            parentGenericArgumentMutator().mutateGenericArgumentInParent(program) shouldBe null
        }
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

        parentGenericArgumentMutator().mutate(program)?.first shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `parent generic argument referencing class from inaccessible module is invalid`() {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.JAVA
            classKind = ClassKind.INTERFACE
            typeParameters += typeParameter
        }
        val invisibleClass = buildClassDeclaration {
            name = "InvisibleClass"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.JAVA
            classKind = ClassKind.OPEN
        }
        val parentType = (parent.type as IrParameterizedClassifier).apply {
            putTypeArgument(typeParameter, child.type)
        }
        child.implementedTypes += parentType
        val m0 = buildModule {
            name = "m0"
            classes += parent
        }
        val m1 = buildModule {
            name = "m1"
            dependencies += m0
            classes += child
        }
        val m2 = buildModule {
            name = "m2"
            dependencies += m1
            classes += invisibleClass
        }
        val program = buildProgram {
            modules += m0
            modules += m1
            modules += m2
        }

        // Since parent and child are the only visible classes to child (child is already the argument, parent is interface),
        // invisibleClass from m2 cannot be picked as replacement argument for child in m1.
        val result = parentGenericArgumentMutator().mutate(program)
        // Either no mutation can be performed or if performed it cannot pick invisibleClass
        (result?.second?.contains("InvisibleClass") == true) shouldBe false
    }

    @Test
    fun `changing a parent argument used only by an inherited member is valid`() {
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

        parentGenericArgumentMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `changing a parent argument invalidates an explicit override using the old argument`() {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val parentFunction = parameterFunction(Language.KOTLIN, typeParameter)
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            typeParameters += typeParameter
            functions += parentFunction
        }
        val oldArgument = buildClassDeclaration {
            name = "OldArgument"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val parentType = parent.type as IrParameterizedClassifier
        parentType.putTypeArgument(typeParameter, oldArgument.type)
        val childFunction = parameterFunction(Language.KOTLIN, oldArgument.type).apply {
            isOverride = true
            override += parentFunction
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            implementedTypes += parentType
            functions += childFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += child
            }
        }

        parentGenericArgumentMutator().mutate(program)?.first shouldBe SemanticValidity.INVALID
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

        parentGenericArgumentMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
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

        removeOverrideMutator().mutate(program)?.first shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `removing a required override with projected parameter types is invalid`() {
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
        val argument = buildClassDeclaration {
            name = "Argument"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val projectedArguments = listOf(
            IrTypeArgument.covariant(argument.type),
            IrTypeArgument.contravariant(argument.type),
            IrTypeArgument.STAR,
        )

        for (projectedArgument in projectedArguments) {
            fun projectedWrapper(): IrParameterizedClassifier {
                return (wrapper.type as IrParameterizedClassifier).apply {
                    putTypeArgument(typeParameter, projectedArgument)
                }
            }
            val parentFunction = parameterFunction(Language.KOTLIN, projectedWrapper()).apply {
                body = null
            }
            val parent = buildClassDeclaration {
                name = "Parent"
                language = Language.KOTLIN
                classKind = ClassKind.INTERFACE
                functions += parentFunction
            }
            val childFunction = parameterFunction(Language.KOTLIN, projectedWrapper()).apply {
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

            removeOverrideMutator().mutate(program)?.first shouldBe SemanticValidity.INVALID
        }
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

        removeOverrideMutator().mutate(program)?.first shouldBe SemanticValidity.INVALID
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

        removeOverrideMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `removing an override is valid when a stub ancestor hides a concrete fallback`() {
        val rootFunction = parameterFunction(Language.JAVA).apply { body = null }
        val root = buildClassDeclaration {
            name = "Root"
            language = Language.JAVA
            classKind = ClassKind.INTERFACE
            functions += rootFunction
        }
        val concreteFunction = parameterFunction(Language.KOTLIN)
        val concreteAncestor = buildClassDeclaration {
            name = "ConcreteAncestor"
            language = Language.KOTLIN
            classKind = ClassKind.ABSTRACT
            implementedTypes += root.type
            functions += concreteFunction
        }
        val stubFunction = parameterFunction(Language.JAVA).apply {
            isOverride = true
            isOverrideStub = true
            override += rootFunction
            override += concreteFunction
        }
        val middle = buildClassDeclaration {
            name = "Middle"
            language = Language.JAVA
            classKind = ClassKind.OPEN
            superType = concreteAncestor.type
            implementedTypes += root.type
            functions += stubFunction
        }
        val targetFunction = parameterFunction(Language.JAVA).apply {
            isOverride = true
            override += rootFunction
            override += stubFunction
        }
        val target = buildClassDeclaration {
            name = "Target"
            language = Language.JAVA
            classKind = ClassKind.OPEN
            superType = middle.type
            implementedTypes += root.type
            functions += targetFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += target
                classes += middle
                classes += concreteAncestor
                classes += root
            }
        }

        removeOverrideMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `removing an override from interface is valid when concrete descendant inherits implementation from intermediate ancestor`() {
        val grandParentFunction = parameterFunction(Language.KOTLIN).apply { body = null }
        val grandParent = buildClassDeclaration {
            name = "GrandParent"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            functions += grandParentFunction
        }
        val parentFunction = parameterFunction(Language.JAVA).apply {
            isOverride = true
            override += grandParentFunction
        }
        val parentInterface = buildClassDeclaration {
            name = "ParentInterface"
            language = Language.JAVA
            classKind = ClassKind.INTERFACE
            implementedTypes += grandParent.type
            functions += parentFunction
        }
        val ancestorFunction = parameterFunction(Language.JAVA).apply {
            isOverride = true
            override += parentFunction
        }
        val abstractAncestor = buildClassDeclaration {
            name = "AbstractAncestor"
            language = Language.JAVA
            classKind = ClassKind.ABSTRACT
            implementedTypes += parentInterface.type
            functions += ancestorFunction
        }
        val concreteDescendant = buildClassDeclaration {
            name = "ConcreteDescendant"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            superType = abstractAncestor.type
            implementedTypes += grandParent.type
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += grandParent
                classes += parentInterface
                classes += abstractAncestor
                classes += concreteDescendant
            }
        }

        val mutator = IrMutator(
            config = MutatorConfig.allZero.copy(removeOverrideMemberFunctionWeight = 1),
            generator = IrDeclGenerator(random = alwaysFalseRandom),
        )
        mutator.mutate(program)?.first shouldBe SemanticValidity.VALID
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

        upperBoundMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
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

        upperBoundMutator().mutate(program)?.first shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `changing a class type parameter upper bound accepts a star argument`() {
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
        val targetType = target.type as IrParameterizedClassifier
        targetType.putTypeArgument(typeParameter, IrTypeArgument.STAR)
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
                classes += target
                classes += user
            }
        }

        upperBoundMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `changing upper bound to depend on a type parameter projected as wildcard in Java usage is invalid`() {
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            superType = parent.type
        }
        val tB = buildTypeParameter {
            name = "TB"
            upperbound = parent.type
        }
        val tA = buildTypeParameter {
            name = "TA"
            upperbound = parent.type
        }
        val box = buildClassDeclaration {
            name = "Box"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            typeParameters += tA
            typeParameters += tB
        }
        val boxUsage = (box.type as IrParameterizedClassifier).apply {
            putTypeArgument(tA, child.type)
            putTypeArgument(tB, IrTypeArgument(IrVariance.COVARIANT, parent.type))
        }
        val javaUser = buildClassDeclaration {
            name = "JavaUser"
            language = Language.JAVA
            classKind = ClassKind.OPEN
            functions += buildFunctionDeclaration {
                name = "foo"
                language = Language.JAVA
                returnType = IrAny
                parameterList = buildParameterList {
                    parameters += buildParameter {
                        name = "p"
                        type = boxUsage
                    }
                }
                body = buildBlock()
            }
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += parent
                classes += child
                classes += box
                classes += javaUser
            }
        }

        val customGenerator = object : IrDeclGenerator(random = alwaysFalseRandom) {
            override fun randomType(
                fromClasses: List<IrClassDeclaration>,
                fromTypeParameters: List<IrTypeParameter>,
                finishTypeArguments: Boolean,
                filter: (IrType) -> Boolean
            ): IrType? {
                return tB
            }
        }
        val mutator = IrMutator(
            config = MutatorConfig.allZero.copy(mutateClassTypeParameterUpperBoundWeight = 1),
            generator = customGenerator,
        )
        val res = mutator.mutate(program)
        res?.first shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `tightening upper bound nullability ignores override stub functions`() {
        val bound = buildClassDeclaration {
            name = "Bound"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
        }
        val tParam = buildTypeParameter {
            name = "T"
            upperbound = buildNullableType { innerType = bound.type }
        }
        val target = buildClassDeclaration {
            name = "Target"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            typeParameters += tParam
        }
        val targetUsage = (target.type as IrParameterizedClassifier).apply {
            putTypeArgument(tParam, buildNullableType { innerType = bound.type })
        }
        val stubFunc = buildFunctionDeclaration {
            name = "stubFoo"
            language = Language.KOTLIN
            returnType = IrAny
            isOverride = true
            isOverrideStub = true
            parameterList = buildParameterList {
                parameters += buildParameter {
                    name = "p"
                    type = targetUsage
                }
            }
            body = buildBlock()
        }
        val user = buildClassDeclaration {
            name = "User"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            functions += stubFunc
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += bound
                classes += target
                classes += user
            }
        }

        val mutator = IrMutator(
            config = MutatorConfig.allZero.copy(mutateClassTypeParameterUpperBoundNullabilityWeight = 1),
            generator = IrDeclGenerator(random = alwaysFalseRandom),
        )
        mutator.mutate(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `relaxing upper bound nullability preserves dependent type parameter self usage`() {
        val bound = buildClassDeclaration {
            name = "Bound"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
        }
        val targetParameter = buildTypeParameter {
            name = "T"
            upperbound = bound.type
        }
        val dependentParameter = buildTypeParameter {
            name = "U"
            upperbound = targetParameter
        }
        val target = buildClassDeclaration {
            name = "Target"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            typeParameters += targetParameter
            typeParameters += dependentParameter
        }
        val selfUsage = (target.type as IrParameterizedClassifier).apply {
            putTypeArgument(targetParameter, dependentParameter)
            putTypeArgument(
                dependentParameter,
                IrTypeArgument(IrVariance.CONTRAVARIANT, dependentParameter),
            )
        }
        target.functions += buildFunctionDeclaration {
            name = "foo"
            language = Language.KOTLIN
            returnType = selfUsage
            parameterList = buildParameterList()
            body = buildBlock()
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += bound
                classes += target
            }
        }

        val mutator = IrMutator(
            config = MutatorConfig.allZero.copy(mutateClassTypeParameterUpperBoundNullabilityWeight = 1),
            generator = IrDeclGenerator(random = alwaysFalseRandom),
        )
        mutator.mutate(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `relaxing upper bound nullability when used as argument for generic class with nullable upper bound is valid`() {
        val bound = buildClassDeclaration {
            name = "Bound"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
        }
        val tOther = buildTypeParameter {
            name = "TOther"
            upperbound = buildNullableType { innerType = bound.type }
        }
        val otherGeneric = buildClassDeclaration {
            name = "OtherGeneric"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            typeParameters += tOther
        }
        val tParam = buildTypeParameter {
            name = "T"
            upperbound = bound.type
        }
        val otherGenericUsage = (otherGeneric.type as IrParameterizedClassifier).apply {
            putTypeArgument(tOther, buildNullableType { innerType = tParam })
        }
        val func = buildFunctionDeclaration {
            name = "foo"
            language = Language.KOTLIN
            returnType = otherGenericUsage
            parameterList = buildParameterList()
            body = buildBlock()
        }
        val target = buildClassDeclaration {
            name = "Target"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            typeParameters += tParam
            functions += func
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += bound
                classes += target
                classes += otherGeneric
            }
        }

        val mutator = IrMutator(
            config = MutatorConfig.allZero.copy(mutateClassTypeParameterUpperBoundNullabilityWeight = 1),
            generator = IrDeclGenerator(random = alwaysFalseRandom),
        )
        val res = mutator.mutate(program)
        res?.first shouldBe SemanticValidity.VALID
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

        returnTypeNullabilityMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
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

        returnTypeNullabilityMutator().mutate(program)?.first shouldBe SemanticValidity.INVALID
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

        returnTypeNullabilityMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `tightening an override return type preserves covariant generic compatibility`() {
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
        val argument = buildClassDeclaration {
            name = "Argument"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val parentReturnType = (wrapper.type as IrParameterizedClassifier).apply {
            putTypeArgument(typeParameter, IrTypeArgument.covariant(IrAny))
        }
        val childReturnType = (wrapper.type as IrParameterizedClassifier).apply {
            putTypeArgument(typeParameter, argument.type)
        }
        val parentFunction = returnTypeFunction(
            Language.KOTLIN,
            buildNullableType { innerType = parentReturnType },
        )
        val childFunction = returnTypeFunction(
            Language.KOTLIN,
            buildNullableType { innerType = childReturnType },
        ).apply {
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

        returnTypeNullabilityMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
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

        returnTypeNullabilityMutator().mutate(program)?.first shouldBe SemanticValidity.INVALID
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
        mutator.mutateReturnTypeNullability(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `Java explicit return type nullability is not a platform type`() {
        val parentFunction = returnTypeFunction(Language.JAVA, IrAny)
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
        mutator.mutateReturnTypeNullability(program)?.first shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `Java return type parameter with nullable upper bound is platform type for Kotlin override`() {
        val typeParam = buildTypeParameter {
            name = "T0"
            upperbound = buildNullableType { innerType = IrAny }
        }
        val parentFunction = returnTypeFunction(
            Language.JAVA,
            buildNullableType { innerType = typeParam },
        )
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.JAVA
            classKind = ClassKind.INTERFACE
            typeParameters += typeParam
            functions += parentFunction
        }
        val childFunction = returnTypeFunction(
            Language.KOTLIN,
            buildNullableType { innerType = IrAny },
        ).apply {
            isOverride = true
            override += parentFunction
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            implementedTypes += (parent.type as IrParameterizedClassifier).apply {
                putTypeArgument(typeParam, IrAny)
            }
            functions += childFunction
        }.apply {
            allSuperTypeArguments[IrTypeParameterName(typeParam.name)] = typeParam to IrAny
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += child
                classes += parent
            }
        }

        val mutator = returnTypeNullabilityMutator()
        mutator.mutateReturnTypeNullability(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `instantiated inherited signature preserves projected type arguments`() {
        val ownerTypeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val wrapperTypeParameter = buildTypeParameter {
            name = "U"
            upperbound = IrAny
        }
        val wrapper = buildClassDeclaration {
            name = "Wrapper"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            typeParameters += wrapperTypeParameter
        }
        val projectedParameterType = (wrapper.type as IrParameterizedClassifier).apply {
            putTypeArgument(wrapperTypeParameter, IrTypeArgument.covariant(ownerTypeParameter))
        }
        val inheritedFunction = parameterFunction(Language.KOTLIN, projectedParameterType)
        val owner = buildClassDeclaration {
            name = "Owner"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            typeParameters += ownerTypeParameter
            functions += inheritedFunction
        }
        val argument = buildClassDeclaration {
            name = "Argument"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val ownerType = owner.type as IrParameterizedClassifier
        ownerType.putTypeArgument(ownerTypeParameter, argument.type)
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.ABSTRACT
            implementedTypes += ownerType
        }

        val signature = child.collectFunctionSignatureMap().keys.single()
        val instantiatedParameter = signature.parameterTypes.single() as IrParameterizedClassifier
        val instantiatedArgument = instantiatedParameter.getTypeArguments().values.single().second
        instantiatedArgument.variance shouldBe IrVariance.COVARIANT
        (instantiatedArgument.type as com.github.xyzboom.codesmith.ir.types.IrClassifier)
            .classDecl shouldBe argument
    }

    @Test
    fun `making a projected interface method abstract stays valid with an explicit override`() {
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
        val argument = buildClassDeclaration {
            name = "Argument"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        fun projectedWrapper(): IrParameterizedClassifier {
            return (wrapper.type as IrParameterizedClassifier).apply {
                putTypeArgument(typeParameter, IrTypeArgument.covariant(argument.type))
            }
        }
        val parentFunction = parameterFunction(Language.KOTLIN, projectedWrapper())
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            functions += parentFunction
        }
        val childFunction = parameterFunction(Language.KOTLIN, projectedWrapper()).apply {
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

        interfaceDefaultMutator().mutate(program)?.first shouldBe SemanticValidity.VALID
    }

    @Test
    fun `interface default mutation skips override stubs`() {
        val stub = parameterFunction(Language.KOTLIN).apply {
            isOverride = true
            isOverrideStub = true
        }
        val intf = buildClassDeclaration {
            name = "Interface"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            functions += stub
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += intf
            }
        }

        interfaceDefaultMutator().mutateInterfaceMethodDefault(program) shouldBe null
    }

    @Test
    fun `making a second projected interface method default creates a diamond conflict`() {
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
        val argument = buildClassDeclaration {
            name = "Argument"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        fun projectedWrapper(): IrParameterizedClassifier {
            return (wrapper.type as IrParameterizedClassifier).apply {
                putTypeArgument(typeParameter, IrTypeArgument.covariant(argument.type))
            }
        }
        val rootFunction = parameterFunction(Language.KOTLIN, projectedWrapper()).apply {
            body = null
        }
        val root = buildClassDeclaration {
            name = "Root"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            functions += rootFunction
        }
        val abstractFunction = parameterFunction(Language.KOTLIN, projectedWrapper()).apply {
            body = null
            isOverride = true
            override += rootFunction
        }
        val abstractBranch = buildClassDeclaration {
            name = "AbstractBranch"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            implementedTypes += root.type
            functions += abstractFunction
        }
        val defaultFunction = parameterFunction(Language.KOTLIN, projectedWrapper()).apply {
            isOverride = true
            override += rootFunction
        }
        val defaultBranch = buildClassDeclaration {
            name = "DefaultBranch"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            implementedTypes += root.type
            functions += defaultFunction
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.ABSTRACT
            implementedTypes += abstractBranch.type
            implementedTypes += defaultBranch.type
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                // The deterministic random selects AbstractBranch first.
                classes += abstractBranch
                classes += defaultBranch
                classes += child
            }
        }

        interfaceDefaultMutator().mutate(program)?.first shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `Scala filters unsupported positive-weight operators before mutation`() {
        val function = parameterFunction(Language.KOTLIN)
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += buildClassDeclaration {
                    name = "C"
                    language = Language.KOTLIN
                    classKind = ClassKind.FINAL
                    functions += function
                }
            }
        }
        val mutator = IrMutator(
            config = MutatorConfig.allZero.copy(mutateParameterNullabilityWeight = 1),
            generator = IrDeclGenerator(random = alwaysFalseRandom),
            languageSemantics = MutatorLanguageSemantics.SCALA,
        )

        mutator.mutate(program) shouldBe null
        function.parameterList.parameters.single().type shouldBe IrAny
    }

    @Test
    fun `Scala keeps generic override parameter mutation enabled`() {
        val (program, _) = genericOverrideParameterProgram()
        val mutator = IrMutator(
            config = MutatorConfig.allZero.copy(
                mutateGenericArgumentInMemberFunctionParameterWeight = 1,
                mutateParameterNullabilityWeight = 100,
            ),
            generator = IrDeclGenerator(random = alwaysFalseRandom),
            languageSemantics = MutatorLanguageSemantics.SCALA,
        )

        mutator.mutate(program)?.first shouldBe SemanticValidity.INVALID
    }

    @Test
    fun `Groovy filters non-annotation operators before mutation`() {
        val (program, childFunction) = genericOverrideParameterProgram(Language.GROOVY4)
        val before = childFunction.parameterList.parameters.single().type
        val mutator = IrMutator(
            config = MutatorConfig.allZero.copy(
                mutateGenericArgumentInMemberFunctionParameterWeight = 1,
            ),
            generator = IrDeclGenerator(random = alwaysFalseRandom),
            languageSemantics = MutatorLanguageSemantics.GROOVY,
        )

        mutator.mutate(program) shouldBe null
        childFunction.parameterList.parameters.single().type shouldBe before
    }

    @Test
    fun `Groovy parameter nullability mutation has unknown validity`() {
        val parentFunction = parameterFunction(Language.GROOVY4)
        val childFunction = parameterFunction(Language.GROOVY4).apply {
            isOverride = true
            override += parentFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += buildClassDeclaration {
                    name = "Child"
                    language = Language.GROOVY4
                    classKind = ClassKind.FINAL
                    functions += childFunction
                }
            }
        }

        groovyMutator(mutateParameterNullabilityWeight = 1)
            .mutate(program)?.first shouldBe SemanticValidity.UNKNOWN
    }

    @Test
    fun `Groovy return nullability mutation has unknown validity`() {
        val parentFunction = returnTypeFunction(Language.GROOVY4, IrAny)
        val childFunction = returnTypeFunction(Language.GROOVY4, IrAny).apply {
            isOverride = true
            override += parentFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += buildClassDeclaration {
                    name = "Child"
                    language = Language.GROOVY4
                    classKind = ClassKind.FINAL
                    functions += childFunction
                }
            }
        }

        groovyMutator(mutateReturnTypeNullabilityWeight = 1)
            .mutate(program)?.first shouldBe SemanticValidity.UNKNOWN
    }

    @Test
    fun `Groovy upper bound nullability mutation has unknown validity`() {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = buildNullableType { innerType = IrAny }
        }
        val target = buildClassDeclaration {
            name = "Target"
            language = Language.GROOVY4
            classKind = ClassKind.OPEN
            typeParameters += typeParameter
        }
        val targetOfNullableAny = target.type as IrParameterizedClassifier
        targetOfNullableAny.putTypeArgument(
            typeParameter,
            buildNullableType { innerType = IrAny },
        )
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += target
                classes += buildClassDeclaration {
                    name = "User"
                    language = Language.GROOVY4
                    classKind = ClassKind.FINAL
                    implementedTypes += targetOfNullableAny
                }
            }
        }

        groovyMutator(mutateClassTypeParameterUpperBoundNullabilityWeight = 1)
            .mutate(program)?.first shouldBe SemanticValidity.UNKNOWN
    }

    @Test
    fun `Groovy non-null to nullable upper bound mutation has unknown validity`() {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += buildClassDeclaration {
                    name = "Target"
                    language = Language.GROOVY4
                    classKind = ClassKind.OPEN
                    typeParameters += typeParameter
                }
            }
        }

        groovyMutator(mutateClassTypeParameterUpperBoundNullabilityWeight = 1)
            .mutate(program)?.first shouldBe SemanticValidity.UNKNOWN
    }

    private fun genericOverrideParameterProgram(
        language: Language = Language.KOTLIN,
    ): Pair<IrProgram, IrFunctionDeclaration> {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val wrapper = buildClassDeclaration {
            name = "Wrapper"
            this.language = language
            classKind = ClassKind.FINAL
            typeParameters += typeParameter
        }
        val oldArgument = buildClassDeclaration {
            name = "OldArgument"
            this.language = language
            classKind = ClassKind.FINAL
        }
        fun wrapperOfOldArgument() = (wrapper.type as IrParameterizedClassifier).apply {
            putTypeArgument(typeParameter, oldArgument.type)
        }
        val parentFunction = parameterFunction(language, wrapperOfOldArgument())
        val childFunction = parameterFunction(language, wrapperOfOldArgument()).apply {
            isOverride = true
            override += parentFunction
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += buildClassDeclaration {
                    name = "Child"
                    this.language = language
                    classKind = ClassKind.FINAL
                    functions += childFunction
                }
            }
        }
        return program to childFunction
    }

    private fun groovyMutator(
        mutateParameterNullabilityWeight: Int = 0,
        mutateReturnTypeNullabilityWeight: Int = 0,
        mutateClassTypeParameterUpperBoundNullabilityWeight: Int = 0,
    ) = IrMutator(
        config = MutatorConfig.allZero.copy(
            mutateParameterNullabilityWeight = mutateParameterNullabilityWeight,
            mutateReturnTypeNullabilityWeight = mutateReturnTypeNullabilityWeight,
            mutateClassTypeParameterUpperBoundNullabilityWeight = mutateClassTypeParameterUpperBoundNullabilityWeight,
        ),
        generator = IrDeclGenerator(random = alwaysFalseRandom),
        languageSemantics = MutatorLanguageSemantics.GROOVY,
    )

    private fun interfaceDefaultMutator() = IrMutator(
        config = MutatorConfig.allZero.copy(mutateInterfaceMethodDefaultWeight = 1),
        generator = IrDeclGenerator(random = alwaysFalseRandom),
    )

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
