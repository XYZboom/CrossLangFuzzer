package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.visitor.IIrVisitor

abstract class IrFile : IIrDeclaration {
    abstract val name: String
    abstract val extension: String
    override fun <R, D> accept(visitor: IIrVisitor<R, D>, data: D): R =
        visitor.visitFile(this, data)
}