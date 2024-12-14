package com.github.xyzboom.codesmith.irOld.declarations.impl

import com.github.xyzboom.codesmith.irOld.IrAccessModifier
import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.declarations.IrCompanionObject
import com.github.xyzboom.codesmith.irOld.declarations.IrFunction
import com.github.xyzboom.codesmith.irOld.types.IrConcreteType
import com.github.xyzboom.codesmith.irOld.types.IrTypeParameter

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