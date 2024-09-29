package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.visitor.IrTopDownVisitor

abstract class AbstractIrClassPrinter(
    val indent: Int = 0
): IrPrinter<IrClass, String>, IrTopDownVisitor<StringBuilder> {
    abstract fun IrClassType.print(): String
    abstract fun IrAccessModifier.print(): String
}