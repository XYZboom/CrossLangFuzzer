package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrClass: IrDeclaration, IrFunctionContainer, IrDeclarationContainer {

    override val declarations: List<IrDeclaration>
        get() = functions

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitClass(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        declarations.forEach { it.accept(visitor, data) }
    }
}