package com.github.xyzboom.codesmith.ir_old.expressions

import com.github.xyzboom.codesmith.ir_old.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir_old.visitor.IrVisitor

class IrFunctionCall(
    val receiver: IrExpression?,
    val target: IrFunctionDeclaration,
    val arguments: List<IrExpression>
) : IrExpression() {
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitFunctionCallExpression(this, data)
    }
}