package com.github.xyzboom.codesmith.printerOld.java

import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.declarations.IrFile
import com.github.xyzboom.codesmith.irOld.declarations.impl.IrClassImpl
import com.github.xyzboom.codesmith.irOld.types.builtin.IrBuiltinTypes
import com.github.xyzboom.codesmith.printerOld.AbstractIrFilePrinter

class IrJavaFilePrinter: AbstractIrFilePrinter() {
    private val stringBuilder = StringBuilder()
    private val classPrinter = IrJavaClassPrinter()
    override fun print(element: IrFile): String {
        stringBuilder.clear()
        visitFile(element, stringBuilder)
        return stringBuilder.toString()
    }

    override fun visitFile(file: IrFile, data: StringBuilder) {
        val fileClassName = file.name.replaceFirstChar { it.uppercaseChar() }
        stringBuilder.append(classPrinter.print(IrClassImpl(fileClassName, file, superType = IrBuiltinTypes.ANY)))
        super.visitFile(file, data)
    }

    override fun visitClass(clazz: IrClass, data: StringBuilder) {
        stringBuilder.append(classPrinter.print(clazz))
        super.visitClass(clazz, data)
    }
}