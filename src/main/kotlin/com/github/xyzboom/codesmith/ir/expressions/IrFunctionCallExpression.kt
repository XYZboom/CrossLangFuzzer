package com.github.xyzboom.codesmith.ir.expressions

import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrFunctionCallExpression: IrExpression {
    val callTarget: IrFunction
    val valueArguments: List<IrExpression>
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitFunctionCallExpression(this, data)
}