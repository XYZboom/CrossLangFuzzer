package com.github.xyzboom.codesmith.printer.kt

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.types.IrClassType.*
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.printer.AbstractIrClassPrinter

class KtIrClassPrinter : AbstractIrClassPrinter() {

    companion object {
        private val builtInNames = buildMap {
            put(IrAny, "Any")
            put(IrNothing, "Nothing")
            put(IrUnit, "Unit")
        }

        /**
         * For java interaction
         */
        const val TOP_LEVEL_CONTAINER_CLASS_NAME = "MainKt"
    }


    override fun print(element: IrClassDeclaration): String {
        val data = StringBuilder()
        visitClass(element, data)
        return data.toString()
    }

    override fun printTopLevelFunctions(program: IrProgram): String {
        val data = StringBuilder()
        elementStack.push(program)
        for (function in program.functions.filter { it.language == Language.KOTLIN }) {
            visitFunction(function, data)
        }
        require(elementStack.pop() === program)
        return data.toString()
    }

    override fun printType(irType: IrType): String {
        return when (irType) {
            is IrNullableType -> return "${printType(irType.innerType)}?"
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
        if (superType != null || implList.isNotEmpty()) {
            sb.append(": ")
        }
        if (superType != null) {
            sb.append(printType(superType))
            sb.append("()")
            if (implList.isNotEmpty()) {
                sb.append(", ")
            }
        }
        if (implList.isNotEmpty()) {
            for ((index, type) in implList.withIndex()) {
                sb.append(printType(type))
                if (index != implList.lastIndex) {
                    sb.append(", ")
                }
            }
        }
        return sb.toString()
    }

    override fun printIrClassType(irClassType: IrClassType): String {
        return when (irClassType) {
            ABSTRACT -> "abstract class "
            INTERFACE -> "interface "
            OPEN -> "open class "
            FINAL -> "class "
        }
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
        data.append(indent)
        if (function.body == null) {
            data.append("abstract ")
        }
        if (function.isOverride) {
            data.append("override ")
        }
        if (!function.isFinal && !function.topLevel) {
            data.append("open ")
        }
        data.append("fun ")
        data.append(function.name)
        data.append("(")
        visitParameterList(function.parameterList, data)
        data.append("): ")
        data.append(printType(function.returnType))
        val body = function.body
        if (body != null) {
            data.append(" {\n")
            indentCount++
            visitBlock(body, data)
            indentCount--
            data.append(indent)
            data.append("}")
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
            data.append(parameter.name)
            data.append(": ")
            data.append(printType(parameter.type))
            if (index != parameters.lastIndex) {
                data.append(", ")
            }
        }
    }

    override fun visitProperty(property: IrPropertyDeclaration, data: StringBuilder) {
        data.append(indent)
        if (!property.isFinal) {
            data.append("open ")
        }
        val valOrVar = if (property.readonly) {
            "val "
        } else {
            "var "
        }
        data.append(valOrVar)
        data.append(property.name)
        data.append(": ")
        data.append(printType(property.type))
        data.append(" = ")
        data.append("TODO()")
        data.append("\n")
    }

    override fun visitBlock(block: IrBlock, data: StringBuilder) {
        data.append("${indent}throw RuntimeException()\n")
    }
}