package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrValueParameter : IrElement {
    val name: String
    val type: IrType
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitValueParameter(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) = Unit
}