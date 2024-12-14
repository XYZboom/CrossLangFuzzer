package com.github.xyzboom.codesmith.ir.visitor

import com.github.xyzboom.codesmith.ir.IrElement

interface IrTopDownVisitor<D>: IrVisitor<Unit, D> {
    override fun visitElement(element: IrElement, data: D) {
        element.acceptChildren(this, data)
    }
}