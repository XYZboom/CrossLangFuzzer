package com.github.xyzboom.codesmith.printer_old

import com.github.xyzboom.codesmith.ir_old.IrElement

interface IrPrinter<in IR: IrElement, out R> {
    fun print(element: IR): R
}