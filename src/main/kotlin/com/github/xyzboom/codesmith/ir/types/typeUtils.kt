package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.builder.buildDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildPlatformType
import com.github.xyzboom.codesmith.ir.types.builder.buildSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.collections.iterator

private val logger = KotlinLogging.logger {}

fun IrParameterizedClassifier.putTypeArgument(typeParameter: IrTypeParameter, type: IrType) {
    val name = IrTypeParameterName(typeParameter.name)
    require(arguments.containsKey(name))
    arguments[name] = typeParameter to type
}

fun IrParameterizedClassifier.getTypeArguments(): Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>> {
    require(arguments.values.all { it.second != null })
    @Suppress("UNCHECKED_CAST")
    // as we check all type argument is not null, this cast is null safe
    return arguments as Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>
}

fun IrType.equalsIgnoreTypeArguments(other: IrType): Boolean {
    return when (this) {
        is IrBuiltInType -> other === this
        is IrClassifier -> {
            if (other !is IrClassifier) return false
            return classDecl == other.classDecl
        }

        is IrNullableType -> {
            if (other !is IrNullableType) return false
            return innerType.equalsIgnoreTypeArguments(other.innerType)
        }

        is IrTypeParameter -> this == other
        else -> throw NoWhenBranchMatchedException("IrType has unexpected type: ${this::class.qualifiedName}")
    }
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
            if (typeArg == null) continue
            val notNullTypeArg = typeArg.notNullType
            if (notNullTypeArg !is IrTypeParameter) continue
            val typeArgAsTypeParameterName = IrTypeParameterName(notNullTypeArg.name)
            if (args.containsKey(typeArgAsTypeParameterName)) {
                val replaceWith = args[typeArgAsTypeParameterName]!!
                logger.trace { "replace ${typeParam.render()} with ${replaceWith.second.render()}" }
                putTypeArgument(typeParam, replaceWith.second)
            }
        }
    }
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
            return if (typeArg.upperbound is IrNullableType) {
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
         * `A<Any>::foo::t` is `Any`, not `Any!`
         * `A<Any?>::foo::t` and `A<Any!>::foo::t` is `Any!`
         */
        if (oriInner is IrTypeParameter && oriInner.upperbound is IrNullableType) {
            return if (typeArg is IrNullableType || typeArg is IrPlatformType) {
                buildPlatformType {  innerType = typeArg.innerType }
            } else {
                typeArg.notPlatformType
            }
        }
    }
    if (oriType is IrParameterizedClassifier) {
        oriType.putAllTypeArguments(typeArguments, onlyValue)
    }
    return oriType
}

fun areEqualTypes(a: IrType?, b: IrType?): Boolean {
    return when (a) {
        is IrParameterizedClassifier -> {
            if (b !is IrParameterizedClassifier) return false
            for ((paramName, pair) in a.arguments) {
                val (_, arg) = pair
                if (paramName !in b.arguments) {
                    return false
                }
                if (!areEqualTypes(arg, b.arguments[paramName]!!.second)) {
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
                IrTypeParameterName(it.name) to (it to null)
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
