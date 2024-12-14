package com.github.xyzboom.codesmith.irOld.expressions

import com.github.xyzboom.codesmith.irOld.IrElement
import com.github.xyzboom.codesmith.irOld.visitor.IrVisitor

sealed interface IrExpression: IrElement {
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitExpression(this, data)
}