package com.github.xyzboom.codesmith.ir_old.visitor

import com.github.xyzboom.codesmith.ir_old.IrElement

interface IrTopDownVisitor<D>: IrVisitor<Unit, D> {
    override fun visitElement(element: IrElement, data: D) {
        element.acceptChildren(this, data)
    }
}