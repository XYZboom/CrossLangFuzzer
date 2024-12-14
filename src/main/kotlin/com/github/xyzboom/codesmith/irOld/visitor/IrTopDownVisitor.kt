package com.github.xyzboom.codesmith.irOld.visitor

import com.github.xyzboom.codesmith.irOld.IrElement

interface IrTopDownVisitor<in D>: IrVisitor<Unit, D> {
    override fun visitElement(element: IrElement, data: D) {
        element.acceptChildren(this, data)
    }
}