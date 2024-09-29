package com.github.xyzboom.codesmith.ir.types.impl

import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter

class IrTypeParameterImpl(
    override val upperBound: IrConcreteType,
    override val name: String
): IrTypeParameter {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IrTypeParameterImpl) return false

        if (upperBound != other.upperBound) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = upperBound.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}