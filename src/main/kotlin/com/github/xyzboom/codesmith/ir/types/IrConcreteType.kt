package com.github.xyzboom.codesmith.ir.types

abstract class IrConcreteType: IrType {
    abstract val arguments: List<IrTypeArgument>
    abstract val superType: IrConcreteType?
    abstract val implementedTypes: MutableList<IrConcreteType>
}