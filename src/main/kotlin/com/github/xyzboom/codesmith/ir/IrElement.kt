package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrElement {
    fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R
}