package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.visitor.IIrVisitor

abstract class IrProgram : IIrDeclaration {
    abstract val modules: MutableList<IrModule>
    override fun <R, D> accept(visitor: IIrVisitor<R, D>, data: D): R =
        visitor.visitProgram(this, data)
}