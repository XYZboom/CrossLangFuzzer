package com.github.xyzboom.codesmith.ir.expressions

import com.github.xyzboom.codesmith.ir.declarations.IrConstructor
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrConstructorCallExpression: IrFunctionCallExpression {
    override val callTarget: IrConstructor
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitConstructorCallExpression(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) = Unit

    companion object {
        internal val anyConstructor = object: IrConstructorCallExpression {
            override val callTarget: IrConstructor
                get() = throw UnsupportedOperationException("this constructor is used only for built-in type")

            override val valueArguments: MutableList<IrExpression>
                get() = throw UnsupportedOperationException("this constructor is used only for built-in type")
        }
    }
}