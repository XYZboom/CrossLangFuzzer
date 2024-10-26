package com.github.xyzboom.codesmith.ir.expressions

import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrTodoExpression: IrExpression {
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitTodoExpression(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
    }
}