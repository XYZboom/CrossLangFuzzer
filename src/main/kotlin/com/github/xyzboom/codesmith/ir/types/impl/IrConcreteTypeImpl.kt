package com.github.xyzboom.codesmith.ir.types.impl

import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.IrTypeArgument
import com.github.xyzboom.codesmith.ir.types.Nullability

class IrConcreteTypeImpl(
    override val name: String,
    override val superType: IrConcreteType? = null,
    override val implementedTypes: MutableList<IrConcreteType> = mutableListOf(),
    override val arguments: List<IrTypeArgument> = emptyList(),
    override var nullability: Nullability = Nullability.NOT_SPECIFIED
): IrConcreteType() {
}