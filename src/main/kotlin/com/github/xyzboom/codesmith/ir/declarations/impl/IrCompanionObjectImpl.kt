package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrCompanionObject
import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter

class IrCompanionObjectImpl(
    override var accessModifier: IrAccessModifier,
    override val containingDeclaration: IrClass,
    override var superType: IrConcreteType? = null,
    override val implementedTypes: MutableList<IrConcreteType> = mutableListOf(),
    override val typeParameters: MutableList<IrTypeParameter> = mutableListOf(),
    override val functions: MutableList<IrFunction> = mutableListOf(),
    override val classes: MutableList<IrClass> = mutableListOf()
): IrCompanionObject() {

}