package com.github.xyzboom.codesmith.ir.types

class IrTypeParameter private constructor(
    val name: String,
    val upperBound: IrType
) : IrType() {
    companion object {
        fun create(name: String, upperBound: IrType): IrTypeParameter {
            return IrTypeParameter(name, upperBound)
        }
    }

    override val classType: IrClassType get() = upperBound.classType

    override fun toString(): String {
        return "IrTypeParameter($name: $upperBound)"
    }

    override fun equalsIgnoreTypeArguments(other: IrType): Boolean {
        return this == other
    }
}