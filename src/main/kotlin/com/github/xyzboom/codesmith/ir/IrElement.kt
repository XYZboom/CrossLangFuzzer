package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

abstract class IrElement {
    open fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitElement(this, data)
    }

    open fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {}
}