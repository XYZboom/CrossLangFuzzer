package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrSpecialConstructor

class IrSpecialConstructorImpl(
    override val containingDeclaration: IrClass,
): IrSpecialConstructor