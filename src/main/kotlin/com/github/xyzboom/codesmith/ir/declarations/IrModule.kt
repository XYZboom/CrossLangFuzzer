package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrModule : IrDeclaration {
    val name: String
    val dependencies: MutableList<IrModule>
    val files: MutableList<IrFile>
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitModule(this, data)
}