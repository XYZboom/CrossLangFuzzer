package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrFunction: IrDeclaration, IrFunctionContainer {
    val name: String
    val containingDeclaration: IrFunctionContainer
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitFunction(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {

    }
}