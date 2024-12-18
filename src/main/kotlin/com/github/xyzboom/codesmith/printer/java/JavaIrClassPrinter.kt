package com.github.xyzboom.codesmith.printer.java

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrClassType.*
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.printer.AbstractIrClassPrinter

class JavaIrClassPrinter : AbstractIrClassPrinter() {
    companion object {
        private val builtInNames = buildMap {
            put(IrAny, "Object")
            put(IrNothing, "Void")
        }
    }

    override fun print(element: IrClassDeclaration): String {
        val data = StringBuilder()
        visitClass(element, data)
        return data.toString()
    }

    override fun printIrClassType(irClassType: IrClassType): String {
        return when (irClassType) {
            ABSTRACT -> "abstract class "
            INTERFACE -> "interface "
            OPEN -> "class "
            FINAL -> "final class "
        }
    }

    override fun printType(irType: IrType): String {
        return when (irType) {
            is IrBuiltInType -> return builtInNames[irType]
                ?: throw IllegalStateException("No such built-in type: $irType")

            is IrClassifier ->
                when (irType) {
                    is IrSimpleClassifier -> irType.classDecl.name
                }

            else -> throw NoWhenBranchMatchedException()
        }
    }

    override fun IrClassDeclaration.printExtendList(superType: IrType?, implList: List<IrType>): String {
        val sb = if (superType != null || implList.isNotEmpty()) {
            StringBuilder(" ")
        } else {
            StringBuilder()
        }
        if (superType != null) {
            sb.append("extends ")
            sb.append(printType(superType))
            sb.append(" ")
        }
        if (implList.isNotEmpty()) {
            if (classType != INTERFACE) {
                sb.append("implements ")
            } else {
                sb.append("extends ")
            }
            for ((index, type) in implList.withIndex()) {
                sb.append(printType(type))
                if (index != implList.lastIndex) {
                    sb.append(", ")
                }
            }
        }
        return sb.toString()
    }

    override fun visitClass(clazz: IrClassDeclaration, data: StringBuilder) {
        data.append(indent)
        data.append("public ")
        data.append(printIrClassType(clazz.classType))
        data.append(clazz.name)
        data.append(clazz.printExtendList(clazz.superType, clazz.implementedTypes))
        data.append(" {\n")

        indentCount++
        super.visitClass(clazz, data)
        indentCount--

        data.append(indent)
        data.append("}\n")
    }

    override fun visitFunction(function: IrFunctionDeclaration, data: StringBuilder) {
        if (function.isOverrideStub) {
            data.append(indent)
            data.append("// stub\n")
            data.append(indent)
            data.append("/*\n")
        }
        val functionContainer: IrElement = elementStack.peek()
        data.append(indent)
        data.append("public ")
        if (function.body == null) {
            data.append("abstract ")
        } else {
            if (functionContainer is IrClassDeclaration && functionContainer.classType == INTERFACE) {
                data.append("default ")
            }
        }
        if (function.isFinal) {
            data.append("final ")
        }
        data.append("void") // todo: change to real return type
        data.append(" ")
        data.append(function.name)
        data.append("(")
        data.append(")")
        if (function.body != null) {
            data.append(" {\n")
            indentCount++
            super.visitFunction(function, data)
            indentCount--
            data.append(indent)
            data.append("}")
        } else {
            data.append(";")
        }
        data.append("\n")
        if (function.isOverrideStub) {
            data.append(indent)
            data.append("*/\n")
        }
    }
}