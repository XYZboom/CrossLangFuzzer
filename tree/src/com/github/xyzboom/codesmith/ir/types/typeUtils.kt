@file:JvmName("treeTypeUtils")

package com.github.xyzboom.codesmith.ir.types

val IrType.notNullType: IrType
    get() = when (this) {
        is IrNullableType, is IrPlatformType -> this.innerType.notNullType
        else -> this
    }

val IrType.notPlatformType: IrType
    get() = if (this is IrPlatformType) {
        this.innerType
    } else {
        this
    }