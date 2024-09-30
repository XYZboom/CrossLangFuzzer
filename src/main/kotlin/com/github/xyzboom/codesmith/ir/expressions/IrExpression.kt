package com.github.xyzboom.codesmith.ir.expressions

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrExpression: IrElement {
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitExpression(this, data)
}