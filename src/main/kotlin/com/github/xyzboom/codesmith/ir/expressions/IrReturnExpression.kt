package com.github.xyzboom.codesmith.ir.expressions

import com.github.xyzboom.codesmith.ir.types.IrType

class IrReturnExpression(val innerExpression: IrExpression) : IrExpression() {
    override var type: IrType?
        get() = innerExpression.type
        set(value) {
            innerExpression.type = value
        }
}