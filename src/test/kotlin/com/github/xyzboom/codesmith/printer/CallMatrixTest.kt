package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeArgument
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The executable-call harness receiver matrix. */
class CallMatrixTest {
    @Test
    fun `diamond paths deduplicate the same declaration and receiver shape`() {
        val rootFunction = function("rootFun", Language.KOTLIN)
        val root = buildClassDeclaration {
            name = "RootInterface"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            functions += rootFunction
        }
        val left = buildClassDeclaration {
            name = "LeftInterface"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            implementedTypes += buildSimpleClassifier { classDecl = root }
        }
        val right = buildClassDeclaration {
            name = "RightInterface"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            implementedTypes += buildSimpleClassifier { classDecl = root }
        }
        val child = buildClassDeclaration {
            name = "DiamondChild"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            implementedTypes += buildSimpleClassifier { classDecl = left }
            implementedTypes += buildSimpleClassifier { classDecl = right }
        }

        val rootCalls = CallMatrix.receivers(listOf(root, left, right, child))
            .flatMap { it.calls }
            .count { it.function === rootFunction }

        // Child-direct and Child-as-Root. The two inheritance paths must not duplicate either.
        assertTrue(rootCalls == 2, "expected 2 root calls, got $rootCalls")
    }

    @Test
    fun `a concrete parent function is called through parent child and child-as-parent`() {
        val parent = buildClassDeclaration {
            name = "Parent"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            functions += function("parentFun", Language.KOTLIN)
        }
        val child = buildClassDeclaration {
            name = "Child"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            superType = buildSimpleClassifier { classDecl = parent }
            functions += function("childFun", Language.KOTLIN)
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += parent
                classes += child
            }
        }

        val output = IrProgramPrinter().printToSingle(program)

        assertTrue(output.contains("ParentReceiver.parentFun()"), output)
        assertTrue(output.contains("ChildReceiver.childFun()"), output)
        assertTrue(output.contains("ChildReceiver.parentFun()"), output)
        assertTrue(output.contains("val ChildAsParentReceiver: Parent = ChildReceiver"), output)
        assertTrue(output.contains("ChildAsParentReceiver.parentFun()"), output)
    }

    @Test
    fun `an interface has no independent instance but remains a child static receiver`() {
        val javaInterface = buildClassDeclaration {
            name = "JavaIface"
            language = Language.JAVA
            classKind = ClassKind.INTERFACE
            functions += function("ifaceFun", Language.JAVA)
        }
        val implementor = buildClassDeclaration {
            name = "Implementor"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            implementedTypes += buildSimpleClassifier { classDecl = javaInterface }
            functions += function("ownFun", Language.KOTLIN)
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += javaInterface
                classes += implementor
            }
        }

        val output = IrProgramPrinter().printToSingle(program)

        assertTrue(output.contains("ImplementorReceiver.ownFun()"), output)
        assertTrue(output.contains("ImplementorReceiver.ifaceFun()"), output)
        assertTrue(output.contains("val ImplementorAsJavaIfaceReceiver: JavaIface = ImplementorReceiver"), output)
        assertTrue(output.contains("ImplementorAsJavaIfaceReceiver.ifaceFun()"), output)
        assertFalse(output.contains("val JavaIfaceReceiver ="), output)
    }

    @Test
    fun `a generic class receiver uses its upper bounds as type arguments`() {
        val typeParameter = buildTypeParameter {
            name = "T0"
            upperbound = IrAny
        }
        val generic = buildClassDeclaration {
            name = "GenericOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            typeParameters += typeParameter
            functions += function("genericFun", Language.KOTLIN)
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += generic
            }
        }

        val output = IrProgramPrinter().printToSingle(program)

        assertTrue(output.contains("val GenericOwnerReceiver = GenericOwner<Any>()"), output)
        assertTrue(output.contains("GenericOwnerReceiver.genericFun()"), output)
    }

    @Test
    fun `dependent nullable upper bounds substitute earlier type parameters`() {
        val first = buildTypeParameter {
            name = "T0"
            upperbound = IrAny
        }
        val second = buildTypeParameter {
            name = "T1"
            upperbound = buildNullableType { innerType = first }
        }
        val recorded = mapOf(IrTypeParameterName(first.name) to (first to IrAny))

        val resolved = CallMatrix.resolveUpperBound(second, recorded)

        assertTrue(com.github.xyzboom.codesmith.ir.types.areEqualTypes(resolved, IrAny), resolved.toString())
    }

    @Test
    fun `a function whose parameter is nullable is called with a null of the exact type`() {
        val owner = buildClassDeclaration {
            name = "NullableOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += function(
                "nullableFun",
                Language.KOTLIN,
                buildNullableType { innerType = IrAny },
            )
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += owner
            }
        }

        val output = IrProgramPrinter().printToSingle(program)

        assertTrue(output.contains("NullableOwnerReceiver.nullableFun((null as Any?))"), output)
    }

    @Test
    fun `Java nullable Void parameters are skipped`() {
        val owner = buildClassDeclaration {
            name = "JavaNullableVoidOwner"
            language = Language.JAVA
            classKind = ClassKind.FINAL
            functions += function(
                "nullableUnit",
                Language.JAVA,
                buildNullableType { innerType = IrUnit },
            )
            functions += function(
                "nullableNothing",
                Language.JAVA,
                buildNullableType { innerType = IrNothing },
            )
        }
        val program = buildProgram { classes += owner }

        val output = IrProgramPrinter().printToSingle(program)

        assertFalse(output.contains("JavaNullableVoidOwnerReceiver"), output)
        assertFalse(output.contains("nullableUnit((null as"), output)
        assertFalse(output.contains("nullableNothing((null as"), output)
    }

    @Test
    fun `nullable parameterized classifier parameters are called with typed null casts`() {
        val typeParameter = buildTypeParameter {
            name = "T0"
            upperbound = IrAny
        }
        val generic = buildClassDeclaration {
            name = "GenericArgument"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            typeParameters += typeParameter
        }
        fun parameterized(argument: IrTypeArgument): IrType =
            (generic.type as IrParameterizedClassifier).apply {
                putTypeArgument(typeParameter, argument)
            }
        val owner = buildClassDeclaration {
            name = "NullableParameterizedOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += function(
                "invariant",
                Language.KOTLIN,
                buildNullableType { innerType = parameterized(IrTypeArgument.invariant(IrAny)) },
            )
            functions += function(
                "covariant",
                Language.KOTLIN,
                buildNullableType { innerType = parameterized(IrTypeArgument.covariant(IrAny)) },
            )
            functions += function(
                "star",
                Language.KOTLIN,
                buildNullableType { innerType = parameterized(IrTypeArgument.STAR) },
            )
        }
        val program = buildProgram {
            classes += generic
            classes += owner
        }

        val output = IrProgramPrinter().printToSingle(program)

        // Every nullable-parameterized parameter receives a typed null cast whose type is
        // exactly the declared parameter type, so overload resolution stays unambiguous.
        assertTrue(output.contains("NullableParameterizedOwnerReceiver.invariant((null as GenericArgument<Any>?))"), output)
        assertTrue(output.contains("NullableParameterizedOwnerReceiver.covariant((null as GenericArgument<out Any>?))"), output)
        assertTrue(output.contains("NullableParameterizedOwnerReceiver.star((null as GenericArgument<*>?))"), output)
    }

    @Test
    fun `interface arguments are skipped when no simple value can be constructed`() {
        val interfaceType = buildClassDeclaration {
            name = "UnconstructibleInterface"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
        }
        val owner = buildClassDeclaration {
            name = "InterfaceArgumentOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += function(
                "takesInterface",
                Language.KOTLIN,
                buildSimpleClassifier { classDecl = interfaceType },
            )
        }
        val program = buildProgram { classes += interfaceType; classes += owner }

        val output = IrProgramPrinter().printToSingle(program)

        assertFalse(output.contains("InterfaceArgumentOwnerReceiver.takesInterface("), output)
    }

    @Test
    fun `dependent function bounds are resolved before printing a call`() {
        val first = buildTypeParameter {
            name = "T0"
            upperbound = IrAny
        }
        val second = buildTypeParameter {
            name = "T1"
            upperbound = buildNullableType { innerType = first }
        }
        val owner = buildClassDeclaration {
            name = "DependentBoundOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += function("dependent", Language.KOTLIN).apply {
                typeParameters += first
                typeParameters += second
            }
        }
        val program = buildProgram { classes += owner }

        val output = IrProgramPrinter().printToSingle(program)

        assertTrue(output.contains("DependentBoundOwnerReceiver.dependent<Any, Any>()"), output)
    }

    @Test
    fun `platform type parameters use their inner concrete value`() {
        val owner = buildClassDeclaration {
            name = "NoCallableOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += function("platformOnly", Language.KOTLIN,
                com.github.xyzboom.codesmith.ir.types.builder.buildPlatformType { innerType = IrAny })
        }
        val program = buildProgram { classes += owner }

        val output = IrProgramPrinter().printToSingle(program)

        // A platform type of a constructible inner type is passed as the concrete inner value.
        assertTrue(output.contains("val NoCallableOwnerReceiver = NoCallableOwner()"), output)
        assertTrue(output.contains("NoCallableOwnerReceiver.platformOnly(Any())"), output)
    }

    @Test
    fun `an unbound type parameter uses its own upper bound`() {
        val typeParameter = buildTypeParameter {
            name = "T0"
            upperbound = IrAny
        }
        val owner = buildClassDeclaration {
            name = "BareTypeParameterOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += function("bareTypeParameterFun", Language.KOTLIN, typeParameter)
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += owner
            }
        }

        val output = IrProgramPrinter().printToSingle(program)

        // A bare type parameter T0 : Any has no constructible value (`Any()` is not a subtype
        // of T0), so the call is skipped instead of emitting a type-mismatching argument.
        assertFalse(output.contains("BareTypeParameterOwnerReceiver.bareTypeParameterFun("), output)
    }

    @Test
    fun `a function whose parameter is a type parameter of its owner is called with the owner argument`() {
        val typeParameter = buildTypeParameter {
            name = "T0"
            upperbound = IrAny
        }
        val owner = buildClassDeclaration {
            name = "OwnerTypeParameterOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            typeParameters += typeParameter
            functions += function("ownerTypeParameterFun", Language.KOTLIN, typeParameter)
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += owner
            }
        }

        val output = IrProgramPrinter().printToSingle(program)

        // The owner's type parameter is instantiated with its upper bound at the receiver, and
        // the parameter typed by it receives a value of that same concrete type.
        assertTrue(output.contains("val OwnerTypeParameterOwnerReceiver = OwnerTypeParameterOwner<Any>()"), output)
        assertTrue(output.contains("OwnerTypeParameterOwnerReceiver.ownerTypeParameterFun(Any())"), output)
    }

    @Test
    fun `a function body uses the standard placeholder exception`() {
        val owner = buildClassDeclaration {
            name = "ReturningOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += function("returningFun", Language.KOTLIN)
            functions += function("unitFun", Language.KOTLIN, IrUnit)
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += owner
            }
        }

        val output = IrProgramPrinter().printToSingle(program)

        assertTrue(output.contains("throw UnsupportedOperationException()"), output)
    }

    private fun function(
        name: String,
        language: Language,
        vararg parameterTypes: IrType,
    ) = buildFunctionDeclaration {
        this.name = name
        this.language = language
        isFinal = true
        body = buildBlock()
        parameterList = buildParameterList {
            parameterTypes.forEachIndexed { index, parameterType ->
                parameters += buildParameter {
                    this.name = "p$index"
                    type = parameterType
                }
            }
        }
    }
}
