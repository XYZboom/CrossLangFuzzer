package com.github.xyzboom.codesmith.printer.java

import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.printer.IrPrinter

class IrJavaClassPrinter: IrPrinter<IrClass, String> {
    override fun print(element: IrClass): String {
        return "class ${element.name} {\n" +
                "}\n"
    }
}