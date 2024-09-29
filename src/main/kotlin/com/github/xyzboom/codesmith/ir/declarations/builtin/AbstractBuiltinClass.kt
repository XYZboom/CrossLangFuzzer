package com.github.xyzboom.codesmith.ir.declarations.builtin

import com.github.xyzboom.codesmith.ir.declarations.impl.IrClassImpl
import com.github.xyzboom.codesmith.ir.types.IrConcreteType

abstract class AbstractBuiltinClass(
    name: String,
    superType: IrConcreteType? = null,
    implementedTypes: MutableList<IrConcreteType> = mutableListOf()
): IrClassImpl("<built-in: $name>", superType, implementedTypes) {
    abstract override val type: IrConcreteType
}