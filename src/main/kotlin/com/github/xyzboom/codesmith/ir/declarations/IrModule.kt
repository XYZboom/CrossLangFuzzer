package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.visitor.IIrVisitor

abstract class IrModule : IIrDeclaration {
    abstract val name: String
    override fun <R, D> accept(visitor: IIrVisitor<R, D>, data: D): R =
        visitor.visitModule(this, data)
}