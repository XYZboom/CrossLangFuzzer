package com.github.xyzboom.codesmith.ir.expressions

import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

class IrNew private constructor(val createType: IrType) : IrExpression() {

    companion object {
        fun create(createType: IrType): IrNew {
            if (createType is IrNullableType) {
                return IrNew(createType.innerType)
            }
            return IrNew(createType)
        }
    }

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitNewExpression(this, data)
    }
}