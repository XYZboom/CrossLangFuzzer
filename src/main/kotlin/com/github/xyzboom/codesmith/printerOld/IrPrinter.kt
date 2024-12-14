package com.github.xyzboom.codesmith.printerOld

import com.github.xyzboom.codesmith.irOld.IrElement

interface IrPrinter<in IR: IrElement, out R> {
    fun print(element: IR): R
}