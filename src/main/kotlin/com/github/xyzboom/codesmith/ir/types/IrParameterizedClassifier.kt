package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import io.github.oshai.kotlinlogging.KotlinLogging

class IrParameterizedClassifier private constructor(classDecl: IrClassDeclaration) : IrClassifier(classDecl) {
    companion object {
        @JvmStatic
        fun create(classDecl: IrClassDeclaration): IrParameterizedClassifier {
            if (classDecl.typeParameters.isEmpty()) {
                throw IllegalArgumentException("classDecl should have type parameter!")
            }
            return IrParameterizedClassifier(classDecl)
        }

        private val logger = KotlinLogging.logger {}
    }

    private val arguments: MutableMap<IrTypeParameter, IrType?> =
        classDecl.typeParameters.associateWith { null }.toMutableMap()

    fun putTypeArgument(typeParameter: IrTypeParameter, type: IrType) {
        require(arguments.containsKey(typeParameter))
        arguments[typeParameter] = type
    }

    fun getTypeArgument(typeParameter: IrTypeParameter) = arguments[typeParameter]

    fun putAllTypeArguments(args: Map<IrTypeParameter, IrType>, onlyValue: Boolean = false) {
        for ((typeParam, typeArg) in arguments) {
            /**
             * Directly use.
             * Parent<T0>
             * Child<T1>: Parent<T1>
             * [args] will be {"T0": "T1"}
             */
            if (!onlyValue && args.containsKey(typeParam)) {
                logger.trace { "replace $typeParam with ${args[typeParam]!!}" }
                putTypeArgument(typeParam, args[typeParam]!!)
            } else {
                /**
                 * Indirectly use.
                 * Parent<T0>
                 * Child<T1>: Parent<T1>
                 * GrandChild<T2>: Child<T2>
                 * [args] will be {"T2": "T1"},
                 * "T1" here matches value in [args] above.
                 */
                if (args.containsKey(typeArg)) {
                    logger.trace { "replace $typeParam with ${args[typeArg]!!}" }
                    putTypeArgument(typeParam, args[typeArg]!!)
                }
            }
        }
    }

    fun getTypeArguments(): Map<IrTypeParameter, IrType> {
        require(arguments.values.all { it != null })
        @Suppress("UNCHECKED_CAST")
        // as we check all value is not null, this cast is null safe
        return arguments as Map<IrTypeParameter, IrType>
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IrParameterizedClassifier) return false
        if (!super.equals(other)) return false

        if (arguments != other.arguments) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + arguments.hashCode()
        return result
    }

    override fun copy(): IrType {
        return create(this.classDecl).apply {
            for ((typeParameter, typeArgument) in this@IrParameterizedClassifier.arguments) {
                arguments[typeParameter.copy()] = typeArgument?.copy()
            }
        }
    }

    override fun toString(): String {
        return "IrParameterizedClassifier(${classDecl.name}<" +
                "${arguments.toList().joinToString(", ") { "${it.first.name}[${it.second}]" }}>)"
    }


}