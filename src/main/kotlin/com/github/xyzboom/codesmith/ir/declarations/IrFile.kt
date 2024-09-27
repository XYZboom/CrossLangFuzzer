package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrFile : IrDeclaration {
    val name: String
    val containingFunctions: MutableList<IrFunction>

    val containingDeclarations: List<IrDeclaration>
        get() = ArrayList<IrDeclaration>().apply {
            addAll(containingFunctions)
        }
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitFile(this, data)
}