package com.github.xyzboom.codesmith.ir.types

class IrNullableType private constructor(val innerType: IrType) : IrType() {
    override val classType: IrClassType = innerType.classType

    companion object {
        @JvmStatic
        fun nullableOf(type: IrType): IrNullableType {
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

    override fun toString(): String {
        return "IrNullableType($innerType)"
    }

    override fun copy(): IrNullableType {
        return nullableOf(innerType.copy())
    }

    override fun equalsIgnoreTypeArguments(other: IrType): Boolean {
        if (other !is IrNullableType) return false
        return innerType.equalsIgnoreTypeArguments(other.innerType)
    }
}