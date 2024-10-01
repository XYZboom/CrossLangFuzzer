package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrConstructor: IrFunction {
    override val containingDeclaration: IrClass
    val superCall: IrConstructorCallExpression
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitConstructor(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        super.acceptChildren(visitor, data)
        superCall.accept(visitor, data)
    }
}