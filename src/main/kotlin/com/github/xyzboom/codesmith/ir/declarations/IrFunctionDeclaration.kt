package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

class IrFunctionDeclaration(name: String): IrDeclaration(name) {
    var body: IrBlock? = null
    var isOverride: Boolean = false
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitFunction(this, data)
    }

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        body?.accept(visitor, data)
    }
}