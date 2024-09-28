package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrFile: IrDeclaration, IrFunctionContainer, IrDeclarationContainer, IrClassContainer {
    val name: String

    override val declarations: List<IrDeclaration>
        get() = ArrayList<IrDeclaration>(initialCapacity = functions.size + classes.size).apply {
            addAll(functions)
            addAll(classes)
        }
    val containingModule: IrModule
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitFile(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        declarations.forEach { it.accept(visitor, data) }
    }
}