package com.github.xyzboom.codesmith.printer.java

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.printer.AbstractIrClassPrinter

class JavaIrClassPrinter: AbstractIrClassPrinter() {
    override fun print(element: IrClassDeclaration): String {
        val data = StringBuilder()
        visitClass(element, data)
        return data.toString()
    }

    override fun visitClass(clazz: IrClassDeclaration, data: StringBuilder) {
        data.append(indent)
        data.append("public class ")
        data.append(clazz.name)
        data.append(" {\n")

        indentCount++
        super.visitClass(clazz, data)
        indentCount--

        data.append(indent)
        data.append("}\n")
    }

    override fun visitFunction(function: IrFunctionDeclaration, data: StringBuilder) {
        data.append(indent)
        data.append("public ")
        data.append("void") // todo: change to real return type
        data.append(" ")
        data.append(function.name)
        data.append("(")
        data.append(") {\n")
        indentCount++
        super.visitFunction(function, data)
        indentCount--
        data.append(indent)
        data.append("}\n")
    }
}