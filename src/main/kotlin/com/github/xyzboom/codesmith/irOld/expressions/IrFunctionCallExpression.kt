package com.github.xyzboom.codesmith.irOld.expressions

import com.github.xyzboom.codesmith.irOld.declarations.IrFunction
import com.github.xyzboom.codesmith.irOld.visitor.IrVisitor

interface IrFunctionCallExpression: IrExpression {
    val receiver: IrExpression? get() = null
    val callTarget: IrFunction
    val valueArguments: List<IrExpression>
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitFunctionCallExpression(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        valueArguments.forEach { it.accept(visitor, data) }
    }
}