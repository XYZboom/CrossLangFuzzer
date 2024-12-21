package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitor.IrTopDownVisitor
import java.util.*

abstract class AbstractIrClassPrinter(
    var indentCount: Int = 0
) : IrPrinter<IrClassDeclaration, String>, IrTopDownVisitor<StringBuilder> {
    val elementStack = Stack<IrElement>()
    override fun visitElement(element: IrElement, data: StringBuilder) {
        elementStack.push(element)
        super.visitElement(element, data)
        elementStack.pop()
    }

    val indent get() = "\t".repeat(indentCount)

    abstract fun printIrClassType(irClassType: IrClassType): String

    abstract fun printType(irType: IrType): String

    abstract fun IrClassDeclaration.printExtendList(superType: IrType?, implList: List<IrType>): String

    abstract fun printTopLevelFunctions(program: IrProgram): String
}