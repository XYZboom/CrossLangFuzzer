package com.github.xyzboom.codesmith.ir.expressions

import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

class IrFunctionCall(
    val receiver: IrExpression?,
    val target: IrFunctionDeclaration,
    val arguments: List<IrExpression>
) : IrExpression() {
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitFunctionCallExpression(this, data)
    }
}