package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.builder.buildParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.containsKey
import kotlin.collections.get
import kotlin.collections.iterator

private val logger = KotlinLogging.logger {}

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
            logger.trace { "replace ${typeParamName.value} with ${args[typeParamName]!!}" }
            putTypeArgument(typeParam, args[typeParamName]!!.second)
        } else {
            /**
             * Indirectly use.
             * Parent<T0>
             * Child<T1>: Parent<T1>
             * GrandChild<T2>: Child<T2>
             * [args] will be {"T2": "T1"},
             * "T1" here matches value in [args] above.
             */
            if (typeArg !is IrTypeParameter) continue
            val typeArgAsTypeParameterName = IrTypeParameterName(typeArg.name)
            if (args.containsKey(typeArgAsTypeParameterName)) {
                logger.trace { "replace $typeParam with ${args[typeArgAsTypeParameterName]!!}" }
                putTypeArgument(typeParam, args[typeArgAsTypeParameterName]!!.second)
            }
        }
    }
}
