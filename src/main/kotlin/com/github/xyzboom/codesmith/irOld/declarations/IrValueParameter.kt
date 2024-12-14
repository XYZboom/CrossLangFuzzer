package com.github.xyzboom.codesmith.irOld.declarations

import com.github.xyzboom.codesmith.irOld.IrElement
import com.github.xyzboom.codesmith.irOld.types.IrType
import com.github.xyzboom.codesmith.irOld.visitor.IrVisitor

interface IrValueParameter : IrElement {
    val name: String
    val type: IrType
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitValueParameter(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) = Unit
}