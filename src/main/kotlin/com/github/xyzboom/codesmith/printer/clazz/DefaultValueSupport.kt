package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.traverseOverride
import com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrPlatformType
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeArgument
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.notNullType
import com.github.xyzboom.codesmith.printer.CallMatrix

/**
 * The value that a generated function returns, and the value that a call site uses as an
 * argument.
 *
 * Every generated function must return *some* value, so that the call in `box()` really
 * executes and the returned type takes part in the compiler's type checking.
 */
sealed interface DefaultValue {
    /** `null`, for a nullable type. */
    data object Null : DefaultValue

    /** The unit value, for `Unit` / `void`. */
    data object UnitValue : DefaultValue

    /** A fresh object, for `Any` / `Object`. */
    data object AnyValue : DefaultValue

    /**
     * A value of a concrete class: a no-arg constructor call.
     *
     * @param typeArguments the concrete type arguments of the constructor call, empty for a
     *        non-generic class.
     */
    data class Constructor(
        val classDecl: IrClassDeclaration,
        val typeArguments: List<IrType>,
    ) : DefaultValue

    /**
     * An anonymous subclass of an abstract class or an interface.
     *
     * `null` is never used for these types: `null as T` fails with a `NullPointerException` once
     * `T` is inferred as a non-null type, and a `null as T` helper function fails the same way,
     * because the cast happens against the inferred non-null type argument. A Java method
     * returning `null` for a `@NotNull` type fails the same way on the Kotlin side, because
     * Kotlin inserts a null check for the annotated return type.
     *
     * @param typeArguments the concrete type arguments of the supertype.
     */
    data class AnonymousSubclass(
        val classDecl: IrClassDeclaration,
        val typeArguments: List<IrType>,
        /** The members to override, in declaration order and without duplicates. */
        val members: List<IrFunctionDeclaration>,
        /**
         * `true` when the supertype is an abstract class, so the anonymous subclass must call its
         * constructor: `object : Abs() { }`. An interface has no constructor.
         */
        val hasConstructor: Boolean,
    ) : DefaultValue

    /**
     * No value can be produced, because the type is generic. The function throws
     * [DefaultValueSupport.UNAVAILABLE_EXCEPTION] and the call site skips exactly that call.
     */
    data object Unavailable : DefaultValue
}

/**
 * Computes the default value of a generated function's return type and of a call argument.
 *
 * The type parameters of the owner and of the function are replaced by their upper bounds, so
 * that a generic declaration can still be called: the receiver is `Owner<UpperBound>()` and a
 * parameter of type `T` becomes a parameter of the upper bound of `T`.
 *
 * The rules are:
 *
 * - `Unit` / `void` -> [DefaultValue.UnitValue]
 * - `Any` / `Object` -> [DefaultValue.AnyValue]
 * - nullable types -> [DefaultValue.Null]
 * - concrete classes -> [DefaultValue.Constructor]
 * - abstract classes and interfaces -> [DefaultValue.AnonymousSubclass]
 * - a type that cannot be resolved to a concrete type -> [DefaultValue.Unavailable]
 */
object DefaultValueSupport {
    /**
     * The exception thrown when a generic value cannot be produced. The call site catches
     * exactly this exception and skips the corresponding call.
     */
    const val UNAVAILABLE_EXCEPTION = "DefaultValueUnavailableException"

    /**
     * The maximum nesting depth of anonymous subclasses. A member that returns the very
     * interface that declares it needs the anonymous subclass of the same type again; without a
     * cap the printer recurses until `StackOverflowError`.
     */
    const val MAX_ANONYMOUS_SUBCLASS_DEPTH = 2

    /**
     * The value of the type [clazz] itself, used where a value of that exact type is needed.
     *
     * A class with type parameters is instantiated with the upper bounds of its own type
     * parameters, because `Nothing` is not valid for every upper bound and the actual type
     * arguments of the declaration are not known here.
     */
    fun valueOfClass(clazz: IrClassDeclaration): DefaultValue {
        if (clazz.typeParameters.isEmpty()) {
            return defaultValue(buildSimpleClassifier { classDecl = clazz }, null, null)
        }
        val typeArguments = clazz.typeParameters.map { upperBoundOf(it) }
        return classValue(clazz, typeArguments, emptyMap(), 0)
    }

    /**
     * The default value of [irType], with the type parameters of the owner and of the function
     * replaced by their upper bounds.
     */
    fun defaultValue(
        irType: IrType,
        owner: IrClassDeclaration?,
        function: IrFunctionDeclaration?,
    ): DefaultValue = defaultValue(irType, owner, function, 0)

    /**
     * The default value of [irType], at the given [depth] of anonymous subclass nesting.
     */
    fun defaultValue(
        irType: IrType,
        owner: IrClassDeclaration?,
        function: IrFunctionDeclaration?,
        depth: Int,
    ): DefaultValue = defaultValue(
        irType,
        substitutionFor(owner, function),
        depth,
    )

    /**
     * The replacement of the type parameters of [owner] and [function] by their upper bounds.
     *
     * The upper bound is the most concrete type that is known to be valid for the parameter, so
     * it is the only choice that lets the call be type checked without knowing the actual type
     * arguments of the declaration.
     */
    fun substitutionFor(
        owner: IrClassDeclaration?,
        function: IrFunctionDeclaration?,
    ): Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>> {
        val result = mutableMapOf<IrTypeParameterName, Pair<IrTypeParameter, IrType>>()
        owner?.typeParameters?.forEach {
            result[IrTypeParameterName(it.name)] = it to CallMatrix.resolveUpperBound(it, result)
        }
        function?.typeParameters?.forEach {
            result[IrTypeParameterName(it.name)] = it to CallMatrix.resolveUpperBound(it, result)
        }
        return result
    }

    /**
     * The upper bound of a type parameter, made non-null so that it can be used as a type
     * argument and as the type of a value.
     */
    fun upperBoundOf(typeParameter: IrTypeParameter): IrType {
        return CallMatrix.resolveUpperBound(typeParameter, emptyMap())
    }

    /** The default value of [irType] at the given [depth] of anonymous subclass nesting. */
    fun defaultValue(
        irType: IrType,
        substitution: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>,
        depth: Int,
    ): DefaultValue = when {
        irType is IrNullableType -> DefaultValue.Null
        irType === IrUnit -> DefaultValue.UnitValue
        irType === IrAny -> DefaultValue.AnyValue
        irType is IrTypeParameter -> {
            // A bare type parameter has no constructible value: its upper bound is a bound, not
            // the parameter type itself, so `return UpperBound()` (or passing an upper-bound
            // value as an argument) fails type checking. Throw the custom "no default value"
            // exception instead.
            DefaultValue.Unavailable
        }
        irType is IrDefinitelyNotNullType -> defaultValue(irType.innerType, substitution, depth)
        // A platform type is not nullable in Kotlin, so `null` is rejected for it. The value of
        // the inner type is used instead.
        irType is IrPlatformType -> defaultValue(irType.innerType, substitution, depth)
        irType is IrSimpleClassifier -> {
            val arguments = irType.classDecl.typeParameters.map { upperBoundOf(it) }
            classValue(irType.classDecl, arguments, substitution, depth)
        }
        irType is IrParameterizedClassifier -> {
            val arguments = concreteArguments(irType, substitution)
            if (arguments == null) {
                DefaultValue.Unavailable
            } else {
                classValue(irType.classDecl, arguments, substitution, depth)
            }
        }
        else -> DefaultValue.Unavailable
    }

    /**
     * The value of a class with the given type arguments.
     *
     * A concrete class is created with a constructor call, an abstract class and an interface
     * with an anonymous subclass.
     */
    private fun classValue(
        clazz: IrClassDeclaration,
        typeArguments: List<IrType>,
        substitution: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>,
        depth: Int,
    ): DefaultValue {
        if (clazz.typeParameters.size != typeArguments.size) return DefaultValue.Unavailable
        if (clazz.classKind == ClassKind.FINAL || clazz.classKind == ClassKind.OPEN) {
            return DefaultValue.Constructor(clazz, typeArguments)
        }
        return DefaultValue.Unavailable
    }

    /**
     * The concrete type arguments of a parameterized classifier.
     *
     * A contravariant argument cannot be reproduced by a constructor call, because the value
     * would have to be of the projected type, so such a classifier has no value.
     */
    private fun concreteArguments(
        classifier: IrParameterizedClassifier,
        substitution: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>,
    ): List<IrType>? {
        val result = mutableListOf<IrType>()
        for (typeParameter in classifier.classDecl.typeParameters) {
            val argument = classifier.arguments[IrTypeParameterName(typeParameter.name)]?.second
            val type = argument?.type ?: upperBoundOf(typeParameter)
            // A type argument that is a bare type parameter (not explicitly mapped by the
            // substitution to a concrete type) cannot be made concrete: substituting its upper
            // bound would change the instantiated type (e.g. `LF27S<T0>` -> `LF27S<Any>`),
            // which is not the declared return type. Fail the whole value as unavailable.
            if (type is IrTypeParameter && substitution[IrTypeParameterName(type.name)] == null) {
                return null
            }
            val resolved = resolveConcrete(type, substitution) ?: return null
            result.add(resolved)
        }
        return result
    }

    /**
     * Resolves a type into a concrete type that can be used as a type argument.
     *
     * A type parameter is replaced by its replacement in [substitution], and a parameterized
     * classifier is rebuilt with its own resolved arguments.
     */
    fun resolveConcrete(
        irType: IrType,
        substitution: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>,
    ): IrType? = when {
        irType is IrTypeParameter -> {
            val replacement = substitution[IrTypeParameterName(irType.name)]?.second
                ?: resolveConcrete(irType.upperbound, substitution)
                ?: IrAny
            resolveConcrete(replacement, substitution)
        }
        irType is IrNullableType -> {
            val inner = resolveConcrete(irType.innerType, substitution) ?: return null
            buildNullableType { innerType = inner }
        }
        irType is IrDefinitelyNotNullType -> resolveConcrete(irType.innerType, substitution)
        irType is IrPlatformType -> resolveConcrete(irType.innerType, substitution)
        irType is IrSimpleClassifier -> {
            if (irType.classDecl.typeParameters.isEmpty()) {
                irType
            } else {
                val arguments = irType.classDecl.typeParameters.map { upperBoundOf(it) }
                val builder = buildParameterizedClassifier {
                    classDecl = irType.classDecl
                    this.arguments = mutableMapOf()
                }
                arguments.forEachIndexed { index, type ->
                    val typeParameter = irType.classDecl.typeParameters[index]
                    builder.arguments[IrTypeParameterName(typeParameter.name)] =
                        typeParameter to IrTypeArgument.invariant(type)
                }
                builder
            }
        }
        irType is IrParameterizedClassifier -> {
            val typeArguments = concreteArguments(irType, substitution) ?: return null
            val builder = buildParameterizedClassifier {
                classDecl = irType.classDecl
                arguments = mutableMapOf()
            }
            typeArguments.forEachIndexed { index, type ->
                val typeParameter = irType.classDecl.typeParameters[index]
                builder.arguments[IrTypeParameterName(typeParameter.name)] =
                    typeParameter to IrTypeArgument.invariant(type)
            }
            builder
        }
        else -> irType
    }

    /**
     * The abstract members of this class and of all its supertypes, in declaration order and
     * without duplicates.
     *
     * Only members that are genuinely abstract are returned:
     *
     * - A member with a body is already implemented and must not be overridden.
     * - A `final` member cannot be overridden, and overriding it is a compile error
     *   (`OVERRIDING_FINAL_MEMBER`). A Java interface method is printed as `public final`, so
     *   it is never overridable from a Kotlin anonymous subclass either.
     */
    fun abstractMembers(
        clazz: IrClassDeclaration,
        visited: MutableSet<IrClassDeclaration> = mutableSetOf(),
    ): List<IrFunctionDeclaration> {
        if (!visited.add(clazz)) return emptyList()
        val inherited = (listOfNotNull(clazz.superType) + clazz.implementedTypes)
            .mapNotNull { it.classDeclarationOrNull() }
        val realFunctions = clazz.functions.filterNot { it.isOverrideStub }
        val own = realFunctions.filter { it.isOverridable() }
        // A member that this class overrides is already implemented here, so the inherited
        // declaration must not be implemented a second time.
        val overridden = buildSet {
            realFunctions.forEach { function ->
                function.traverseOverride { add(it) }
            }
        }
        val inheritedMembers = inherited.flatMap { abstractMembers(it, visited) }
        return own + inheritedMembers.filter { it !in overridden }
    }

    /**
     * Whether this member can be overridden by an anonymous subclass.
     *
     * A member is overridable when it has no body and is not `final`. A Java interface method
     * carries the `final` flag in this IR even though Java interfaces declare abstract methods,
     * because the Java printer emits `public final` for it; overriding such a member is the
     * `OVERRIDING_FINAL_MEMBER` compile error.
     */
    fun IrFunctionDeclaration.isOverridable(): Boolean =
        body == null && !isFinal && !isOverrideStub

    /** The class declaration of a classifier type, if it has one. */
    fun IrType.classDeclarationOrNull(): IrClassDeclaration? = when (this) {
        is IrSimpleClassifier -> classDecl
        is IrParameterizedClassifier -> classDecl
        is IrNullableType -> innerType.classDeclarationOrNull()
        is IrPlatformType -> innerType.classDeclarationOrNull()
        is IrDefinitelyNotNullType -> innerType.classDeclarationOrNull()
        else -> null
    }
}
