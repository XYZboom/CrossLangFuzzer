package com.github.xyzboom.codesmith.irOld.declarations

import com.github.xyzboom.codesmith.irOld.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.irOld.visitor.IrVisitor

interface IrConstructor: IrFunction {
    override val containingDeclaration: IrClass
    override val containingClass: IrClass get() = containingDeclaration
    val superCall: IrConstructorCallExpression
    override val name: String get() = containingDeclaration.name
    override val returnType get() = containingDeclaration.type
    override fun isSameSignature(other: IrFunction): Boolean {
        if (other !is IrConstructor) return false
        return super.isSameSignature(other)
    }

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitConstructor(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        super.acceptChildren(visitor, data)
        superCall.accept(visitor, data)
    }
}