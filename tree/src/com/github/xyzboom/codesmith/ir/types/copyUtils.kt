package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter

fun IrTypeParameter.copy(): IrTypeParameter {
    return buildTypeParameter {
        name = this@copy.name
        upperbound = this@copy.upperbound.copy()
    }
}

fun IrType.copy(): IrType {
    return when (this) {
        is IrNullableType -> buildNullableType {
            innerType = this@copy.innerType.copy()
        }

        is IrTypeParameter -> copy()

        is IrParameterizedClassifier -> buildParameterizedClassifier {
            classDecl = this@copy.classDecl
            arguments = HashMap()
            for ((typeParameterName, pair) in this@copy.arguments) {
                val (typeParameter, typeArgument) = pair
                val newTypeParameter = buildTypeParameter {
                    name = typeParameterName.value
                    upperbound = typeParameter.upperbound
                }
                arguments[typeParameterName] = newTypeParameter to typeArgument?.copy()
            }
        }

        else -> this
    }
}