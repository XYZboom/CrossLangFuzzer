package com.github.xyzboom.codesmith.ir.types.impl

import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.IrTypeArgument
import com.github.xyzboom.codesmith.ir.types.Nullability

class IrConcreteTypeImpl(
    override val name: String,
    override val declaration: IrClass,
    override val arguments: List<IrTypeArgument> = emptyList(),
    override var nullability: Nullability = Nullability.NOT_SPECIFIED,
    override val classType: IrClassType = IrClassType.FINAL
): IrConcreteType() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IrConcreteTypeImpl) return false

        if (name != other.name) return false
        if (arguments != other.arguments) return false
        if (nullability != other.nullability) return false
        if (classType != other.classType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + arguments.hashCode()
        result = 31 * result + nullability.hashCode()
        result = 31 * result + classType.hashCode()
        return result
    }
}