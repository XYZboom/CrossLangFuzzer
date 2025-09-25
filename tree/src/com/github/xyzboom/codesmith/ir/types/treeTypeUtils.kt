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

/**
 * unwrap means if [this] is a [IrNullableType], a [IrPlatformType] or a [IrDefinitelyNotNullType],
 * the [IrTypeContainer.innerType].[deepUnwrap] will be returned.
 */
fun IrType.deepUnwrap(): IrType {
    return when (this) {
        is IrTypeContainer -> innerType.deepUnwrap()
        else -> this
    }
}