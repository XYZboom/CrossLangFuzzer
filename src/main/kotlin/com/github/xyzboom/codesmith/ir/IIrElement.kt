package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.visitor.IIrVisitor

interface IIrElement {
    fun <R, D> accept(visitor: IIrVisitor<R, D>, data: D): R
}