package com.github.xyzboom.codesmith.irOld.expressions

import com.github.xyzboom.codesmith.irOld.visitor.IrVisitor

sealed interface IrConstantExpression: IrExpression {
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitConstantExpression(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
    }

    data object True: IrConstantExpression
    data object False: IrConstantExpression
    class Number(val value: Int): IrConstantExpression
}