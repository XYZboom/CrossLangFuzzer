package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrProgram: IrDeclaration {
    val modules: MutableList<IrModule>
    var mainModule: IrModule
    val hasMainModule: Boolean
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitProgram(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        modules.forEach { it.accept(visitor, data) }
    }
}