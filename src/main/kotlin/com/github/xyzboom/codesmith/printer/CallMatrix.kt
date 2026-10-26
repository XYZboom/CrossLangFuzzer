package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.Visibility
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrPlatformType
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.IrVariance
import com.github.xyzboom.codesmith.ir.types.areEqualTypes
import com.github.xyzboom.codesmith.ir.types.getActualTypeFromArguments
import com.github.xyzboom.codesmith.ir.types.getConcreteTypeArguments
import com.github.xyzboom.codesmith.ir.types.notNullType
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.type

/** One declaration invoked through one receiver shape. */
data class MatrixCall(
    val function: IrFunctionDeclaration,
    /** Actual owner type used to substitute the declaration's class type parameters. */
    val declaringType: IrType,
)

/**
 * One receiver shape in the executable call matrix.
 *
 * [receiverClass] is the concrete class that is instantiated. [staticType] is the type exposed
 * to overload resolution. They are equal for normal subclass/parent-instance calls and differ
 * for a subclass-as-parent call.
 */
data class MatrixReceiver(
    val receiverClass: IrClassDeclaration,
    val staticType: IrType,
    val calls: MutableList<MatrixCall> = mutableListOf(),
)

/** Builds the complete inherited receiver matrix for constructible concrete classes. */
object CallMatrix {
    /**
     * The executable harness is emitted in a separate module. Until it models package/module and
     * protected-receiver access rules, only public declarations are safe to invoke there.
     */
    fun ownFunctions(clazz: IrClassDeclaration): List<IrFunctionDeclaration> =
        clazz.functions.filterNot { it.isOverrideStub || it.visibility != com.github.xyzboom.codesmith.ir.Visibility.PUBLIC }

    fun isInstantiable(clazz: IrClassDeclaration): Boolean =
        clazz.classKind == ClassKind.FINAL || clazz.classKind == ClassKind.OPEN

    /**
     * Build receiver shapes for every public constructible class. Public Java/Kotlin nested
     * declarations are included only when every enclosing class is public; Scala nested types are
     * omitted because the Kotlin harness does not model their path-dependent type syntax.
     *
     * For a concrete class `C`, an inherited declaration from `P` is called through `C`, through
     * a standalone `P` instance when `P` is constructible, and through the same `C` instance
     * statically typed as the actual inherited `P<...>` type. Grandparents are traversed with
     * structural type-argument substitution. Function identity plus receiver shape is used for
     * deduplication, so diamond paths do not duplicate calls.
     */
    fun receivers(classes: List<IrClassDeclaration>): List<MatrixReceiver> {
        val matrixClasses = mutableListOf<IrClassDeclaration>()
        val seenClasses = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<IrClassDeclaration, Boolean>(),
        )
        fun addClass(clazz: IrClassDeclaration) {
            if (seenClasses.add(clazz)) matrixClasses += clazz
        }
        classes.filter { it.visibility == Visibility.PUBLIC }.forEach(::addClass)
        fun addPublicNestedClasses(clazz: IrClassDeclaration, enclosingAccessible: Boolean) {
            for (nested in clazz.classes) {
                val accessible = enclosingAccessible &&
                    nested.language in setOf(Language.JAVA, Language.KOTLIN) &&
                    nested.visibility == Visibility.PUBLIC
                if (accessible) addClass(nested)
                addPublicNestedClasses(nested, accessible)
            }
        }
        classes.forEach { root ->
            addPublicNestedClasses(root, root.visibility == com.github.xyzboom.codesmith.ir.Visibility.PUBLIC)
        }

        val result = mutableListOf<MatrixReceiver>()

        fun receiver(receiverClass: IrClassDeclaration, staticType: IrType): MatrixReceiver {
            return result.firstOrNull {
                it.receiverClass === receiverClass && areEqualTypes(it.staticType, staticType)
            } ?: MatrixReceiver(receiverClass, staticType).also(result::add)
        }

        fun addCall(
            receiverClass: IrClassDeclaration,
            staticType: IrType,
            function: IrFunctionDeclaration,
            declaringType: IrType,
        ) {
            if (!isPreciseClassifierType(staticType) || !isPreciseClassifierType(declaringType)) return
            val shape = receiver(receiverClass, staticType)
            if (shape.calls.none { it.function === function }) {
                shape.calls += MatrixCall(function, declaringType)
            }
        }

        for (clazz in matrixClasses) {
            if (!isInstantiable(clazz)) continue
            val concreteClassType = concreteType(clazz)
            ownFunctions(clazz).forEach { function ->
                addCall(clazz, concreteClassType, function, concreteClassType)
            }

            for ((parent, actualParentType) in inheritedTypes(clazz, concreteClassType)) {
                for (function in ownFunctions(parent)) {
                    // C instance with its normal C static type.
                    addCall(clazz, concreteClassType, function, actualParentType)
                    // Independent declaring-parent instance, only for a constructible parent.
                    if (seenClasses.contains(parent) && isInstantiable(parent)) {
                        val concreteParentType = concreteType(parent)
                        addCall(parent, concreteParentType, function, concreteParentType)
                    }
                    // The same C instance viewed as the actual P<...> inherited type, when P
                    // is independently accessible to the harness.
                    if (seenClasses.contains(parent)) {
                        addCall(clazz, actualParentType, function, actualParentType)
                    }
                }
            }
        }
        return result.filter { it.calls.isNotEmpty() }
    }

    /**
     * The concrete type of [clazz]: every type parameter is replaced by its upper bound's
     * non-null concrete type, so the no-arg receiver respects declared upper bounds instead of
     * violating them with `Nothing` (which triggers UPPER_BOUND_VIOLATED for `T : Any`).
     */
    fun concreteType(clazz: IrClassDeclaration): IrType {
        val type = clazz.type
        if (type is IrParameterizedClassifier) {
            // Substitute in declaration order so dependent bounds (<T0, T1 : T0>) see the
            // already-resolved concrete types of their dependencies.
            val recorded = mutableMapOf<IrTypeParameterName, Pair<IrTypeParameter, IrType>>()
            clazz.typeParameters.forEach { typeParameter ->
                val bound = resolveUpperBound(typeParameter, recorded)
                recorded[IrTypeParameterName(typeParameter.name)] = typeParameter to bound
                type.putTypeArgument(typeParameter, bound)
            }
        }
        return type
    }

    /**
     * The concrete upper bound of [typeParameter], resolving references to previously recorded
     * type parameters through [recorded]. Falls back to `Any` when no concrete type is known.
     */
    fun resolveUpperBound(
        typeParameter: IrTypeParameter,
        recorded: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>,
    ): IrType {
        val resolved = getActualTypeFromArguments(typeParameter.upperbound, recorded, true)
        val result = when (resolved) {
            is IrNullableType -> resolved.innerType
            is IrPlatformType -> resolved.innerType
            else -> resolved
        }
        return result.notNullType
    }

    private fun inheritedTypes(
        clazz: IrClassDeclaration,
        concreteClassType: IrType,
    ): List<Pair<IrClassDeclaration, IrType>> {
        val result = mutableListOf<Pair<IrClassDeclaration, IrType>>()
        val pending = ArrayDeque<Pair<IrClassDeclaration, IrType>>()
        val rootArguments = concreteArguments(concreteClassType)
        directSuperTypes(clazz).forEach { declaredType ->
            val actualType = getActualTypeFromArguments(declaredType, rootArguments, true)
            actualType.classDeclarationOrNull()?.let { pending += it to actualType }
        }

        while (pending.isNotEmpty()) {
            val (parent, actualParentType) = pending.removeFirst()
            if (!isPreciseClassifierType(actualParentType)) continue
            if (result.any { (seenParent, seenType) ->
                    seenParent === parent && areEqualTypes(seenType, actualParentType)
                }) {
                continue
            }
            result += parent to actualParentType

            val parentArguments = concreteArguments(actualParentType)
            directSuperTypes(parent).forEach { declaredType ->
                val actualType = getActualTypeFromArguments(declaredType, parentArguments, true)
                actualType.classDeclarationOrNull()?.let { pending += it to actualType }
            }
        }
        return result
    }

    private fun directSuperTypes(clazz: IrClassDeclaration): List<IrType> =
        listOfNotNull(clazz.superType) + clazz.implementedTypes

    private fun concreteArguments(
        type: IrType,
    ) = (type as? IrParameterizedClassifier)?.getConcreteTypeArguments().orEmpty()

    private fun IrType.classDeclarationOrNull(): IrClassDeclaration? = when (this) {
        is IrSimpleClassifier -> classDecl
        is IrParameterizedClassifier -> classDecl
        is IrNullableType -> innerType.classDeclarationOrNull()
        is IrPlatformType -> innerType.classDeclarationOrNull()
        is IrDefinitelyNotNullType -> innerType.classDeclarationOrNull()
        else -> null
    }

    /**
     * A receiver type must be reproducible exactly in Kotlin source. Star/projection arguments
     * and unresolved type parameters are skipped rather than producing an inevitably invalid
     * cast or variable declaration.
     */
    private fun isPreciseClassifierType(type: IrType): Boolean = when (type) {
        is IrSimpleClassifier -> true
        is IrParameterizedClassifier -> type.arguments.values.all { (_, argument) ->
            argument.variance == IrVariance.INVARIANT &&
                argument.type?.let(::isPreciseTypeArgument) == true
        }
        else -> false
    }

    private fun isPreciseTypeArgument(type: IrType): Boolean = when (type) {
        is IrTypeParameter -> false
        is IrNullableType -> isPreciseTypeArgument(type.innerType)
        is IrPlatformType -> isPreciseTypeArgument(type.innerType)
        is IrDefinitelyNotNullType -> isPreciseTypeArgument(type.innerType)
        is IrParameterizedClassifier -> isPreciseClassifierType(type)
        else -> true
    }
}
