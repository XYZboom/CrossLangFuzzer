package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrProgram : IrDeclaration {
    val modules: MutableList<IrModule>
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitProgram(this, data)
}