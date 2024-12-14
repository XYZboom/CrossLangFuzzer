package com.github.xyzboom.codesmith.irOld.declarations.impl

import com.github.xyzboom.codesmith.irOld.IrAccessModifier
import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.declarations.IrSpecialConstructor

class IrSpecialConstructorImpl(
    override val containingDeclaration: IrClass,
): IrSpecialConstructor {
    override var accessModifier: IrAccessModifier = IrAccessModifier.PUBLIC
}