package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitor.IrTopDownVisitor
import com.github.xyzboom.codesmith.printer.IrPrinter
import java.util.*

abstract class AbstractIrClassPrinter(
    var indentCount: Int = 0
) : IrPrinter<IrClassDeclaration, String>, IrTopDownVisitor<StringBuilder> {
    val elementStack = Stack<IrElement>()
    open val spaceCountInIndent = 4
    override fun visitElement(element: IrElement, data: StringBuilder) {
        elementStack.push(element)
        super.visitElement(element, data)
        require(elementStack.pop() === element)
    }

    val indent get() = " ".repeat(spaceCountInIndent).repeat(indentCount)

    abstract fun printIrClassType(irClassType: IrClassType): String

    abstract fun IrClassDeclaration.printExtendList(superType: IrType?, implList: List<IrType>): String

    abstract fun printTopLevelFunctionsAndProperties(program: IrProgram): String

    /**
     * @param printNullableAnnotation Print nullability annotation with comment when set to false.
     *          Print full nullability annotation when set to true.
     *          **NOT AVAILABLE** when [noNullabilityAnnotation] set true.
     *          **NOT AVAILABLE** in Kotlin.
     * @param noNullabilityAnnotation Print no nullability annotation of types when set to true.
     *          Suppress [printNullableAnnotation] when [noNullabilityAnnotation] set to true.
     *          **NOT AVAILABLE** in Kotlin.
     */
    abstract fun printType(
        irType: IrType,
        printNullableAnnotation: Boolean = true,
        noNullabilityAnnotation: Boolean = false
    ): String
}