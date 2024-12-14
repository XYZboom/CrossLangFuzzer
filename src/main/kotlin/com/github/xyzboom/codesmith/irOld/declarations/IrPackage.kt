package com.github.xyzboom.codesmith.irOld.declarations

import com.github.xyzboom.codesmith.irOld.IrElement
import com.github.xyzboom.codesmith.irOld.visitor.IrVisitor

interface IrPackage: IrElement {
    val name: String
    val parent: IrPackage?
    val containingModule: IrModule
    val files: MutableList<IrFile>
    val fullName: String
        get() = if (parent != null) "${parent!!.name}.$name" else name

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitPackage(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        files.forEach { it.accept(visitor, data) }
    }
}