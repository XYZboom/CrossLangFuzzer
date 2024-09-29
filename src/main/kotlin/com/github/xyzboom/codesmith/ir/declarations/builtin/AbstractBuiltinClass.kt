package com.github.xyzboom.codesmith.ir.declarations.builtin

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.declarations.impl.IrClassImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrFileImpl
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType

abstract class AbstractBuiltinClass(
    name: String,
    superType: IrConcreteType? = null,
    classType: IrClassType = IrClassType.FINAL,
    implementedTypes: MutableList<IrConcreteType> = mutableListOf()
): IrClassImpl(
    "<built-in: $name>", IrFileImpl.builtin, IrAccessModifier.PUBLIC,
    classType, superType, implementedTypes
) {
    abstract override val type: IrConcreteType
}