package com.github.xyzboom.codesmith.irOld.expressions

import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.visitor.IrVisitor

interface IrAnonymousObject: IrExpression {
    val superClass: IrClass
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitAnonymousObject(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
    }
}