package com.github.xyzboom.codesmith.printer.kt

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.printer.IrPrinter

class KtIrClassPrinter: IrPrinter<IrClassDeclaration, String> {
    override fun print(element: IrClassDeclaration): String {
        return "public class ${element.name}"
    }
}