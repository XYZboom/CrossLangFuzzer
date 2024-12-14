package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

class IrClassDeclaration(
    name: String,
    val fields: MutableList<IrFieldDeclaration> = mutableListOf(),
    val functions: MutableList<IrFunctionDeclaration> = mutableListOf(),
): IrDeclaration(name) {
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitClass(this, data)
    }

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        fields.forEach { it.accept(visitor, data) }
        functions.forEach { it.accept(visitor, data) }
    }
}