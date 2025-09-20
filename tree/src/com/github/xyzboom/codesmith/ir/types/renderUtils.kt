package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.declarations.render
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType

fun IrType.render(): String {
    return when (this) {
        is IrNullableType -> "IrNullableType(${innerType.render()})"
        is IrParameterizedClassifier -> "IrParameterizedClassifier(${classDecl.name}<" +
                "${arguments.toList().joinToString(", ") { "${it.first.value}[${it.second.second?.render()}]" }}>)"
        is IrSimpleClassifier -> classDecl.render()
        is IrTypeParameter -> "IrTypeParameter($name: ${upperbound.render()})"
        is IrBuiltInType -> this::class.simpleName!!
        is IrDefinitelyNotNullType -> "IrDefinitelyNotNullType(${innerType.render()})"
        is IrPlatformType -> "IrPlatformType(${innerType.render()})"
        else -> throw NoWhenBranchMatchedException("Unexpected IrType ${this::class.qualifiedName}")
    }
}