package com.github.xyzboom.codesmith.ir.visitors

import com.github.xyzboom.codesmith.ir.IrElement

abstract class IrTopDownVisitor<D>: IrVisitor<Unit, D>() {
    override fun visitElement(element: IrElement, data: D) {
        element.acceptChildren(this, data)
    }
}