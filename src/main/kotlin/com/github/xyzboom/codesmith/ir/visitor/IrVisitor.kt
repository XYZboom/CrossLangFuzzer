package com.github.xyzboom.codesmith.ir.visitor

import com.github.xyzboom.codesmith.ir.IrElement

interface IrVisitor<out R, in D> {
    fun visitElement(element: IrElement, data: D): R
}