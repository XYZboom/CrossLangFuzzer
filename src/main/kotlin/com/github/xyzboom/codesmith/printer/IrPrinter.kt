package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.IrElement

interface IrPrinter<in IR: IrElement, out R> {
    fun print(element: IR): R
}