package com.github.xyzboom.codesmith.ir_old.expressions

import com.github.xyzboom.codesmith.ir_old.types.IrType
import com.github.xyzboom.codesmith.ir_old.visitor.IrVisitor

class IrVariable(
    val name: String,
    val varType: IrType
): IrExpression() {
    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {

    }
}