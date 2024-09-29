package com.github.xyzboom.codesmith.printer.kt

import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrFile
import com.github.xyzboom.codesmith.printer.AbstractIrFilePrinter

class IrKtFilePrinter: AbstractIrFilePrinter() {

    private val stringBuilder = StringBuilder()
    private val ktClassPrinter = IrKtClassPrinter()
    override fun print(element: IrFile): String {
        stringBuilder.clear()
        visitFile(element, stringBuilder)
        return stringBuilder.toString()
    }

    override fun visitFile(file: IrFile, data: StringBuilder) {
        stringBuilder.append(
            "// FILE: ${file.name}.kt\n" +
                    "package ${file.containingPackage.fullName}\n"
        )
        super.visitFile(file, data)
    }

    override fun visitClass(clazz: IrClass, data: StringBuilder) {
        stringBuilder.append(ktClassPrinter.print(clazz))
        super.visitClass(clazz, data)
    }
}