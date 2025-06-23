package com.github.xyzboom.codesmith.ir_old.expressions

import com.github.xyzboom.codesmith.ir_old.types.IrType
import com.github.xyzboom.codesmith.ir_old.visitor.IrVisitor

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