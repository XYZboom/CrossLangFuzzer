package com.github.xyzboom.codesmith.ir_old.expressions

import com.github.xyzboom.codesmith.ir_old.types.IrType
import com.github.xyzboom.codesmith.ir_old.visitor.IrVisitor

class IrDefaultImpl(
    val implType: IrType
): IrExpression() {

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitDefaultImplExpression(this, data)
    }
}