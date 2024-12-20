package com.github.xyzboom.codesmith.printer.java

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.types.IrClassType.*
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.printer.AbstractIrClassPrinter

class JavaIrClassPrinter : AbstractIrClassPrinter() {
    companion object {
        private val builtInNames = buildMap {
            put(IrAny, "Object")
            put(IrNothing, "Void")
            put(IrUnit, "void")
        }

        private const val IMPORTS =
            "import org.jetbrains.annotations.NotNull;\n" +
                    "import org.jetbrains.annotations.Nullable;\n"
    }

    private var printNullableAnnotation = false
    private var noNullableAnnotation = false

    override fun print(element: IrClassDeclaration): String {
        val data = StringBuilder(IMPORTS)
        printNullableAnnotation = element.printNullableAnnotations
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
        val typeStr = when (irType) {
            is IrNullableType -> printType(irType.innerType)

            is IrBuiltInType -> return builtInNames[irType]
                ?: throw IllegalStateException("No such built-in type: $irType")

            is IrClassifier ->
                when (irType) {
                    is IrSimpleClassifier -> irType.classDecl.name
                }

            else -> throw NoWhenBranchMatchedException()
        }
        val annotationStr = if (printNullableAnnotation && !noNullableAnnotation) {
            if (irType is IrNullableType) "@Nullable "
            else "@NotNull"
        } else ""
        return "$annotationStr $typeStr"
    }

    override fun IrClassDeclaration.printExtendList(superType: IrType?, implList: List<IrType>): String {
        noNullableAnnotation = true
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
        noNullableAnnotation = false
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
        data.append(printType(function.returnType))
        data.append(" ")
        data.append(function.name)
        data.append("(")
        visitParameterList(function.parameterList, data)
        data.append(")")
        val body = function.body
        if (body != null) {
            data.append(" {\n")
            indentCount++
            visitBlock(body, data)
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

    override fun visitParameterList(parameterList: IrParameterList, data: StringBuilder) {
        val parameters = parameterList.parameters
        for ((index, parameter) in parameters.withIndex()) {
            data.append(printType(parameter.type))
            data.append(" ")
            data.append(parameter.name)
            if (index != parameters.lastIndex) {
                data.append(", ")
            }
        }
    }

    override fun visitBlock(block: IrBlock, data: StringBuilder) {
        data.append("${indent}throw new RuntimeException();\n")
    }
}