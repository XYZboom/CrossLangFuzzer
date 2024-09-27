package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrFunction: IrDeclaration {
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitFunction(this, data)
}