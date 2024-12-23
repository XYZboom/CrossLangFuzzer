package com.github.xyzboom.codesmith.ir.expressions

import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

class IrVariable(
    val name: String,
    val varType: IrType
): IrExpression() {
    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {

    }
}