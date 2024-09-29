package com.github.xyzboom.codesmith.printer.kt

import com.github.xyzboom.codesmith.ir.declarations.IrFile
import com.github.xyzboom.codesmith.printer.IrPrinter

class IrKtFilePrinter: IrPrinter<IrFile, String> {
    override fun print(element: IrFile): String {
        return "// FILE: ${element.name}.kt\n" +
                "package ${element.containingPackage.fullName}\n"
    }
}