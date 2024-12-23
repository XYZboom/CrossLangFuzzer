package com.github.xyzboom.codesmith.ir.expressions

import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

class IrReturnExpression(val innerExpression: IrExpression?) : IrExpression() {
    override var type: IrType?
        get() = innerExpression?.type
        set(value) {
            innerExpression?.type = value
        }

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitReturnExpression(this, data)
    }
}