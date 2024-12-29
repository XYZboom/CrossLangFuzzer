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

    override fun copy(): IrTypeParameter {
        return IrTypeParameter(name, upperBound)
    }

    /**
     * Since we generate all type parameters with different names,
     * no need to compare other properties.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IrTypeParameter) return false

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}