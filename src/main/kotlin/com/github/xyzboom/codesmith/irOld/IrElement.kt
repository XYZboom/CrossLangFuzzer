package com.github.xyzboom.codesmith.irOld

import com.github.xyzboom.codesmith.irOld.visitor.IrVisitor

interface IrElement {
    fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R

    fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D)
}