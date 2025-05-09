package com.github.xyzboom.codesmith.newprinter.clazz

import io.github.xyzboom.bf.tree.INode
import com.github.xyzboom.codesmith.bf.generated.ICrossLangFuzzerDefTopDownVisitor
import com.github.xyzboom.codesmith.newir.ClassKind
import com.github.xyzboom.codesmith.newir.decl.IrClassDeclaration
import com.github.xyzboom.codesmith.newir.IrProgram
import com.github.xyzboom.codesmith.newprinter.IrPrinter
import com.github.xyzboom.codesmith.printer.TypeContext
import java.util.*

abstract class AbstractIrClassPrinter<D>(
    var indentCount: Int = 0
) : IrPrinter<IrClassDeclaration, String>, ICrossLangFuzzerDefTopDownVisitor<D> {
    val nodeStack = Stack<INode>()
    open val spaceCountInIndent = 4

    override fun visitNode(node: INode, data: D) {
        nodeStack.push(node)
        super.visitNode(node, data)
        require(nodeStack.pop() === node)
    }

    val indent get() = " ".repeat(spaceCountInIndent).repeat(indentCount)

    abstract fun printClassKind(classKind: ClassKind): String

//    abstract fun IrClassDeclaration.printExtendList(superType: IrType?, implList: List<IrType>): String

//    abstract fun printTopLevelFunctionsAndProperties(program: IrProgram): String

    /**
     * @param printNullableAnnotation Print nullability annotation with comment when set to false.
     *          Print full nullability annotation when set to true.
     *          **NOT AVAILABLE** when [noNullabilityAnnotation] set true.
     *          **NOT AVAILABLE** in Kotlin.
     * @param noNullabilityAnnotation Print no nullability annotation of types when set to true.
     *          Suppress [printNullableAnnotation] when [noNullabilityAnnotation] set to true.
     *          **NOT AVAILABLE** in Kotlin.
     */
    /*abstract fun printType(
        irType: IrType,
        typeContext: TypeContext = TypeContext.Other,
        printNullableAnnotation: Boolean = true,
        noNullabilityAnnotation: Boolean = false
    ): String*/
}