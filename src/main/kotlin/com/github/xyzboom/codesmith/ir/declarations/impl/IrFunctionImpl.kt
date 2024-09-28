package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionContainer

class IrFunctionImpl(
    override val name: String,
    override val containingDeclaration: IrFunctionContainer
): IrFunction {
    override val functions: MutableList<IrFunction> = ArrayList()
}