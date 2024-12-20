package com.github.xyzboom.codesmith.ir.types

class IrNullableType private constructor(val innerType: IrType): IrType() {
    companion object {
        @JvmStatic
        fun nullableOf(type: IrType): IrType {
            if (type is IrNullableType) {
                return type
            }
            return IrNullableType(type)
        }
    }
}