package com.github.xyzboom.codesmith.irOld.types.impl

import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.types.*

open class IrConcreteTypeImpl(
    override val name: String,
    override val declaration: IrClass,
    override val arguments: List<IrTypeArgument> = emptyList(),
    override val nullability: Nullability = Nullability.NOT_SPECIFIED,
    override val classType: IrClassType = IrClassType.FINAL
): IrConcreteType() {

    override fun equalsIgnoreNullability(other: IrType): Boolean {
        if (this === other) return true
        if (other !is IrConcreteTypeImpl) return false

        if (name != other.name) return false
        if (arguments != other.arguments) return false
        if (classType != other.classType) return false

        return true
    }

    override fun equals(other: Any?): Boolean {
        if (other !is IrConcreteTypeImpl) return false
        if (!equalsIgnoreNullability(other)) return false
        if (nullability != other.nullability) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + arguments.hashCode()
        result = 31 * result + nullability.hashCode()
        result = 31 * result + classType.hashCode()
        return result
    }

    override fun copy(nullability: Nullability): IrType {
        return IrConcreteTypeImpl(name, declaration, arguments, nullability, classType)
    }
}