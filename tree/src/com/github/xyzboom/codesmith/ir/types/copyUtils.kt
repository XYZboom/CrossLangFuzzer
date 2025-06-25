package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter

fun IrType.copy(): IrType {
    return when (this) {
        is IrNullableType -> buildNullableType {
            innerType = this@copy.innerType
        }

        is IrTypeParameter -> buildTypeParameter {
            name = this@copy.name
            upperbound = this@copy.upperbound
        }

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