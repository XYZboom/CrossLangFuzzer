package com.github.xyzboom.codesmith.irOld.expressions

import com.github.xyzboom.codesmith.irOld.declarations.IrConstructor
import com.github.xyzboom.codesmith.irOld.visitor.IrVisitor

interface IrConstructorCallExpression: IrFunctionCallExpression {
    override val callTarget: IrConstructor
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitConstructorCallExpression(this, data)

    companion object {
        internal val anyConstructor = object: IrConstructorCallExpression {
            override val callTarget: IrConstructor
                get() = throw UnsupportedOperationException("this constructor is used only for built-in type")

            override val valueArguments: MutableList<IrExpression>
                get() = throw UnsupportedOperationException("this constructor is used only for built-in type")
        }
    }
}