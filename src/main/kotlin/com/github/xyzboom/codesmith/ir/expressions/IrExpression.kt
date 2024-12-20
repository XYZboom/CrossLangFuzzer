package com.github.xyzboom.codesmith.ir.expressions

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

abstract class IrExpression : IrElement() {
    open var type: IrType? = null
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitExpression(this, data)
    }
}