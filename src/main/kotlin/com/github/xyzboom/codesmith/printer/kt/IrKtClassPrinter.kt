package com.github.xyzboom.codesmith.printer.kt

import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.printer.IrPrinter

class IrKtClassPrinter(val indent: Int = 0): IrPrinter<IrClass, String> {
    override fun print(element: IrClass): String {
        return "${"\t".repeat(indent)}class ${element.name}\n"
    }
}