package com.github.xyzboom.codesmith.ir_old.expressions

import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.xyzboom.codesmith.ir_old.types.IrNullableType
import com.github.xyzboom.codesmith.ir_old.types.IrType
import com.github.xyzboom.codesmith.ir_old.visitor.IrVisitor

@JsonTypeName("IrNew")
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