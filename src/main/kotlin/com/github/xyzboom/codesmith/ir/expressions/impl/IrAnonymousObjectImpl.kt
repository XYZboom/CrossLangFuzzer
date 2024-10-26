package com.github.xyzboom.codesmith.ir.expressions.impl

import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.expressions.IrAnonymousObject

class IrAnonymousObjectImpl(
    override val superClass: IrClass
): IrAnonymousObject {
}