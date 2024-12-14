package com.github.xyzboom.codesmith.irOld.declarations

import com.github.xyzboom.codesmith.irOld.IrElement
import com.github.xyzboom.codesmith.irOld.visitor.IrVisitor

interface IrProgram: IrElement {
    val modules: MutableList<IrModule>
    var mainModule: IrModule
    val hasMainModule: Boolean
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitProgram(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        modules.forEach { it.accept(visitor, data) }
    }
}