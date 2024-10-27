package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.ir.expressions.IrFunctionCallExpression
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.IrTypeArgument
import com.github.xyzboom.codesmith.ir.visitor.IrTopDownVisitor
import java.util.*

abstract class AbstractIrClassPrinter(
    var indentCount: Int = 0
): IrPrinter<IrClass, String>, IrTopDownVisitor<StringBuilder> {
    val elementStack = Stack<IrElement>()
    override fun visitElement(element: IrElement, data: StringBuilder) {
        elementStack.push(element)
        super.visitElement(element, data)
        elementStack.pop()
    }
    val indent get() = "\t".repeat(indentCount)
    abstract fun IrClassType.print(): String
    abstract fun IrAccessModifier.print(): String
    abstract fun IrClass.printExtendList(): String
    abstract fun IrClass.printTypeParameters(): String
    abstract fun IrTypeArgument.print(): String

    open fun printIrConcreteType(concreteType: IrConcreteType): String {
        with(concreteType) {
            return if (arguments.isEmpty()) {
                fullName
            } else {
                val stringBuilder = StringBuilder()
                stringBuilder.append(fullName)
                stringBuilder.append("<")
                for ((index, arg) in arguments.withIndex()) {
                    stringBuilder.append(arg.print())
                    if (index != arguments.lastIndex) {
                        stringBuilder.append(", ")
                    }
                }
                stringBuilder.append(">")
                stringBuilder.toString()
            }
        }
    }

    override fun visitFunctionCallExpression(functionCallExpression: IrFunctionCallExpression, data: StringBuilder) {
        if (functionCallExpression is IrConstructorCallExpression) {
            return
        }
        val receiver = functionCallExpression.receiver
        val callTarget = functionCallExpression.callTarget
        val clazz = callTarget.containingClass
        val valueArgs = functionCallExpression.valueArguments
        if (receiver == null) {
            if (clazz != null) {
                data.append(printIrConcreteType(clazz.type))
                data.append(".")
            }
        } else {
            elementStack.push(functionCallExpression)
            receiver.accept(this, data)
            assert(elementStack.pop() === functionCallExpression)
            data.append(".")
        }
        data.append(callTarget.name)
        data.append("(")
        for ((i, arg) in valueArgs.withIndex()) {
            elementStack.push(functionCallExpression)
            arg.accept(this, data)
            assert(elementStack.pop() === functionCallExpression)
            if (i != valueArgs.lastIndex) {
                data.append(", ")
            }
        }
        data.append(")")
    }
}