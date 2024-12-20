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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IrNullableType) return false

        if (innerType != other.innerType) return false

        return true
    }

    override fun hashCode(): Int {
        return innerType.hashCode()
    }

}