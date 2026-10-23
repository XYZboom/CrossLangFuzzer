package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.builder.buildDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildPlatformType
import com.github.xyzboom.codesmith.ir.types.builder.buildSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.collections.iterator

private val logger = KotlinLogging.logger {}

fun IrParameterizedClassifier.putTypeArgument(typeParameter: IrTypeParameter, type: IrType) {
    val name = IrTypeParameterName(typeParameter.name)
    require(arguments.containsKey(name))
    arguments[name] = typeParameter to IrTypeArgument.invariant(type)
}

fun IrParameterizedClassifier.putTypeArgument(typeParameter: IrTypeParameter, typeArgument: IrTypeArgument) {
    val name = IrTypeParameterName(typeParameter.name)
    require(arguments.containsKey(name))
    arguments[name] = typeParameter to typeArgument
}

fun IrParameterizedClassifier.getTypeArguments(): Map<IrTypeParameterName, Pair<IrTypeParameter, IrTypeArgument>> {
    return arguments
}

fun IrParameterizedClassifier.getConcreteTypeArguments(): Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>> {
    val result = mutableMapOf<IrTypeParameterName, Pair<IrTypeParameter, IrType>>()
    for ((k, v) in arguments) {
        val inner = v.second.type ?: continue
        result[k] = v.first to inner
    }
    return result
}

fun IrType.equalsIgnoreTypeArguments(other: IrType): Boolean {
    return when (this) {
        is IrBuiltInType -> other === this
        is IrClassifier -> {
            if (other !is IrClassifier) return false
            classDecl == other.classDecl
        }

        is IrNullableType -> {
            if (other !is IrNullableType) return false
            innerType.equalsIgnoreTypeArguments(other.innerType)
        }

        is IrTypeParameter -> this == other
        else -> throw NoWhenBranchMatchedException("IrType has unexpected type: ${this::class.qualifiedName}")
    }
}

/**
 * replace [this] from [originalType] into [newType].
 * for example: [this]: `A<T0, B<T1?>>`; [originalType]: `T1`; [newType]: `X`.
 * The result will be: `A<T0, B<X?>>`
 */
fun IrType.replaceType(originalType: IrType, newType: IrType): IrType {
    return accept(IrTypeReplacer(originalType, newType), null) as IrType
}

fun IrParameterizedClassifier.putAllTypeArguments(
    args: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>,
    onlyValue: Boolean = false
) {
    for ((typeParamName, pair) in arguments) {
        val (typeParam, typeArg) = pair
        /**
         * Directly use.
         * Parent<T0>
         * Child<T1>: Parent<T1>
         * [args] will be {"T0": "T1"}
         */
        if (!onlyValue && args.containsKey(typeParamName)) {
            val replaceWith = args[typeParamName]!!
            logger.trace { "replace ${typeParamName.value} with ${replaceWith.second.render()}" }
            putTypeArgument(typeParam, replaceWith.second)
        } else {
            /**
             * Indirectly use.
             * Parent<T0>
             * Child<T1>: Parent<T1>
             * GrandChild<T2>: Child<T2>
             * [args] will be {"T2": "T1"},
             * "T1" here matches value in [args] above.
             */
            val innerType = typeArg.type ?: continue
            val notNullTypeArg = innerType.notNullType
            if (notNullTypeArg !is IrTypeParameter) continue
            val typeArgAsTypeParameterName = IrTypeParameterName(notNullTypeArg.name)
            if (args.containsKey(typeArgAsTypeParameterName)) {
                val replaceWith = args[typeArgAsTypeParameterName]!!
                logger.trace { "replace ${typeParam.render()} with ${replaceWith.second.render()}" }
                val newInner = if (innerType is IrNullableType) {
                    buildNullableType { this.innerType = replaceWith.second }
                } else {
                    replaceWith.second
                }
                putTypeArgument(typeParam, typeArg.copy(type = newInner))
            }
        }
    }
}

@JvmName("containsTypeArgument")
operator fun Map<IrTypeParameterName, Pair<IrTypeParameter, IrTypeArgument>>.contains(key: IrTypeParameter): Boolean {
    return containsKey(IrTypeParameterName(key.name))
}

@JvmName("getTypeArgument")
operator fun Map<IrTypeParameterName, Pair<IrTypeParameter, IrTypeArgument>>.get(key: IrTypeParameter): IrTypeArgument? {
    return get(IrTypeParameterName(key.name))?.second
}

operator fun MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>.set(
    key: IrTypeParameter, value: IrType
) {
    this[IrTypeParameterName(key.name)] = key to value
}

operator fun Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>.contains(key: IrTypeParameter): Boolean {
    return containsKey(IrTypeParameterName(key.name))
}

operator fun Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>.get(key: IrTypeParameter): IrType? {
    return get(IrTypeParameterName(key.name))?.second
}

/**
 * ```kotlin
 * interface I<T0> {
 *     fun func(i: I<Any>)
 * }
 * ```
 * For `i` in `func`, its type is "I(T0 [ Any ])".
 * If we have a class implements I<String>, the [typeArguments] here will be "T0 [ String ]".
 * For such situation, [onlyValue] must be `true`.
 * @see [IrParameterizedClassifier.putAllTypeArguments]
 */
fun getActualTypeFromArguments(
    oriType: IrType,
    typeArguments: Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>,
    onlyValue: Boolean
): IrType {
    if (oriType is IrTypeParameter) {
        if (oriType in typeArguments) {
            // replace type parameter in super with type argument
            return typeArguments[oriType]!!
        }
    }
    if (oriType is IrNullableType) {
        // if typeArg is platform type, it is correct because we got `TypeArg!?` which is still `TypeArg?`
        return buildNullableType {
            innerType = getActualTypeFromArguments(oriType.innerType, typeArguments, onlyValue)
        }
    }
    if (oriType is IrDefinitelyNotNullType) {
        val typeArg = getActualTypeFromArguments(oriType.innerType, typeArguments, onlyValue)
        if (typeArg is IrTypeParameter) {
            return buildDefinitelyNotNullType { innerType = typeArg }
        }
        // if typeArg is platform or nullable type, we make it not null
        return typeArg.notNullType
    }
    if (oriType is IrPlatformType) {
        val oriInner = oriType.innerType
        val typeArg = getActualTypeFromArguments(oriInner, typeArguments, onlyValue)
        if (typeArg is IrTypeParameter) {
            return if (typeArg.deepUpperboundNullable()) {
                buildPlatformType { innerType = typeArg }
            } else {
                typeArg
            }
        }
        /**
         * ```kt
         * class A<T: Any?> {
         *     fun foo(t: T!)
         * }
         * ```
         * 1. `A<Any>::foo::t` is `Any`, not `Any!`
         * 2. `A<Any?>::foo::t` is different in Kotlin and Java.
         *    `A<Any?>::foo::t` in Kotlin is `Any?`
         *    `A<Any?>::foo::t` in Java is `Any!`
         * 3. `A<Any!>::foo::t` is `Any!`
         */
        if (oriInner is IrTypeParameter) {
            return if (oriInner.deepUpperboundNullable()) {
                if (typeArg is IrNullableType || typeArg is IrPlatformType) {
                    buildPlatformType { innerType = typeArg }
                } else {
                    typeArg.notPlatformType
                }
            } else {
                typeArg
            }
        }
        return buildPlatformType { innerType = typeArg }
    }
    if (oriType is IrParameterizedClassifier) {
        return oriType.copy().apply {
            putAllTypeArguments(typeArguments, onlyValue)
        }
    }
    return oriType
}

fun areEqualTypes(a: IrType?, b: IrType?): Boolean {
    return when (a) {
        is IrParameterizedClassifier -> {
            if (b !is IrParameterizedClassifier) return false
            if (a.classDecl !== b.classDecl) return false
            if (a.arguments.size != b.arguments.size) return false
            for ((paramName, pair) in a.arguments) {
                val (_, arg) = pair
                val otherArg = b.arguments[paramName]?.second ?: return false
                if (arg.variance != otherArg.variance) return false
                if (!areEqualTypes(arg.type, otherArg.type)) {
                    return false
                }
            }
            return true
        }

        is IrClassifier -> {
            if (b !is IrClassifier) return false
            if (a.classDecl !== b.classDecl) return false
            true
        }

        is IrNullableType -> {
            if (b !is IrNullableType) return false
            areEqualTypes(a.innerType, b.innerType)
        }

        is IrTypeParameter -> {
            if (b !is IrTypeParameter) return false
            a.name == b.name
        }

        else -> a === b
    }
}

val IrClassDeclaration.type: IrClassifier
    get() = if (typeParameters.isEmpty()) {
        buildSimpleClassifier {
            classDecl = this@type
        }
    } else {
        buildParameterizedClassifier {
            classDecl = this@type
            arguments = HashMap(classDecl.typeParameters.associate {
                IrTypeParameterName(it.name) to (it to IrTypeArgument.STAR)
            })
        }
    }

/**
 * ```kt
 * class A<T0: Any?, T1: T0> {
 *     //            ^^
 *     // the upperbound of T1 is not IrNullableType
 *     // but we could not say parameter `t` is DNN
 *     fun func(t: T1)
 * }
 * ```
 * ```java
 * class A<T0 extends @Nullable Object, T1 extend T0> {
 *     public void func(T1 t);
 *     //               ^^
 *     // if we make `T1` into `@NotNull T1`, the type of `t` will be DNN
 *     // which is not correct.
 * }
 * ```
 */
fun IrTypeParameter.deepUpperboundNullable(): Boolean {
    val upperbound = upperbound
    if (upperbound is IrNullableType) {
        return true
    }
    if (upperbound is IrTypeParameter) {
        return upperbound.deepUpperboundNullable()
    }
    return false
}

fun IrTypeParameter.upperboundHave(other: IrTypeParameter): Boolean {
    if (areEqualTypes(this.upperbound, other)) {
        return true
    }
    val upperbound = this.upperbound
    return if (upperbound is IrTypeParameter) {
        upperbound.upperboundHave(other)
    } else {
        false
    }
}

fun IrClassDeclaration.isSubClassOf(other: IrClassDeclaration): Boolean {
    if (this === other || name == other.name) return true
    val superType = superType
    if (superType is IrClassifier && superType.classDecl.isSubClassOf(other)) {
        return true
    }
    for (implementedType in implementedTypes) {
        if (implementedType is IrClassifier && implementedType.classDecl.isSubClassOf(other)) {
            return true
        }
    }
    return false
}

fun IrType.containsTypeParameter(target: IrTypeParameter): Boolean = when (this) {
    is IrTypeParameter -> this === target || name == target.name
    is IrTypeContainer -> innerType.containsTypeParameter(target)
    is IrParameterizedClassifier -> arguments.values.any { (_, argument) ->
        argument.type?.containsTypeParameter(target) == true
    }
    else -> false
}

fun IrType.containsTypeParameter(): Boolean = when (this) {
    is IrTypeParameter -> true
    is IrTypeContainer -> innerType.containsTypeParameter()
    is IrParameterizedClassifier -> arguments.values.any { (_, argument) ->
        argument.type?.containsTypeParameter() == true
    }
    else -> false
}

fun IrType.dependsOnTypeParameter(
    target: IrTypeParameter,
    visited: MutableSet<IrTypeParameter> = mutableSetOf(),
): Boolean = when (this) {
    is IrTypeParameter -> {
        if (this === target || name == target.name) {
            true
        } else if (!visited.add(this)) {
            false
        } else {
            upperbound.dependsOnTypeParameter(target, visited)
        }
    }
    is IrTypeContainer -> innerType.dependsOnTypeParameter(target, visited)
    is IrParameterizedClassifier -> arguments.values.any { (_, argument) ->
        argument.type?.dependsOnTypeParameter(target, visited) == true
    }
    else -> false
}

fun IrTypeArgument.isSubArgumentOf(other: IrTypeArgument): Boolean {
    if (other.isStar) return true
    return when (other.variance) {
        IrVariance.STAR -> true
        IrVariance.COVARIANT -> {
            val otherType = other.type ?: return false
            when (this.variance) {
                IrVariance.INVARIANT, IrVariance.COVARIANT -> {
                    this.type?.isSubTypeOf(otherType) == true
                }
                else -> false
            }
        }
        IrVariance.CONTRAVARIANT -> {
            val otherType = other.type ?: return false
            when (this.variance) {
                IrVariance.INVARIANT, IrVariance.CONTRAVARIANT -> {
                    otherType.isSubTypeOf(this.type ?: return false)
                }
                else -> false
            }
        }
        IrVariance.INVARIANT -> {
            if (this.variance != IrVariance.INVARIANT) false
            else areEqualTypes(this.type, other.type)
        }
    }
}

fun IrType.isSubTypeOf(other: IrType): Boolean {
    if (areEqualTypes(this, other)) {
        return true
    }
    if (other === IrAny) {
        return if (this !is IrTypeParameter) {
            this !is IrNullableType
        } else {
            !this.deepUpperboundNullable()
        }
    }
    if (this === IrNothing) {
        return true
    }
    if (other is IrNullableType && this !is IrNullableType) {
        return this.isSubTypeOf(other.innerType)
    }
    return when (this) {
        is IrBuiltInType -> false
        is IrPlatformType -> this.innerType.isSubTypeOf(other)
        is IrTypeParameter -> when (other) {
            is IrTypeParameter -> this.upperboundHave(other)
            is IrClassifier, is IrNullableType -> this.upperbound.isSubTypeOf(other)
            else -> false
        }
        is IrClassifier -> when (other) {
            is IrTypeParameter -> false
            is IrSimpleClassifier -> this.classDecl.isSubClassOf(other.classDecl)
            is IrParameterizedClassifier -> {
                if (this is IrParameterizedClassifier && this.classDecl == other.classDecl) {
                    val thisArgs = this.arguments
                    val otherArgs = other.arguments
                    thisArgs.size == otherArgs.size && thisArgs.all { (k, v) ->
                        val otherArg = otherArgs[k]?.second ?: return@all false
                        val thisArg = v.second
                        thisArg.isSubArgumentOf(otherArg)
                    }
                } else {
                    this.classDecl.isSubClassOf(other.classDecl)
                }
            }
            is IrNullableType -> false
            else -> false
        }
        is IrNullableType -> if (other !is IrNullableType) {
            false
        } else {
            this.innerType.isSubTypeOf(other.innerType)
        }
        else -> false
    }
}
