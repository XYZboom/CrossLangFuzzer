package com.github.xyzboom.codesmith.printer.java

import com.github.xyzboom.codesmith.ir.declarations.IrFile
import com.github.xyzboom.codesmith.printer.IrPrinter

class IrJavaFilePrinter: IrPrinter<IrFile, String> {
    override fun print(element: IrFile): String {
        return "// FILE: ${element.name}.java\n" +
                "package ${element.containingPackage.fullName};\n"
    }
}