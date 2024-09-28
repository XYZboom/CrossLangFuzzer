package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionContainer
import com.github.xyzboom.codesmith.ir.types.IrType

class IrFunctionImpl(
    override val name: String,
    override val containingDeclaration: IrFunctionContainer
): IrFunction {
    override val returnType: IrType
        get() = TODO("Not yet implemented")
    override val functions: MutableList<IrFunction> = ArrayList()

}