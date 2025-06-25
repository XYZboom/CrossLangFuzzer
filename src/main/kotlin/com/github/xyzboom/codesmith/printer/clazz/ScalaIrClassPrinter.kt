package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.ClassKind.*
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.printer.TypeContext

class ScalaIrClassPrinter : AbstractIrClassPrinter() {
    override val spaceCountInIndent: Int = 2

    companion object {
        private val builtInNames = buildMap {
            put(IrAny, "Object")
            put(IrNothing, "Void")
            put(IrUnit, "Unit")
        }

        const val FUNCTION_BODY_TODO = "???"
    }

    override fun printIrClassType(irClassType: ClassKind): String {
        return when (irClassType) {
            ABSTRACT -> "abstract class "
            INTERFACE -> "trait "
            OPEN -> "open class "
            FINAL -> "class "
        }
    }

    override fun printType(
        irType: IrType,
        typeContext: TypeContext,
        printNullableAnnotation: Boolean,
        noNullabilityAnnotation: Boolean
    ): String {
        val typeStr = when (irType) {
            is IrNullableType -> {
                val result = printType(irType.innerType)
                result
            }

            is IrBuiltInType -> builtInNames[irType]
                ?: throw IllegalStateException("No such built-in type: $irType")

            is IrClassifier ->
                when (irType) {
                    is IrSimpleClassifier -> irType.classDecl.name
                    is IrParameterizedClassifier -> {
                        val sb = StringBuilder(irType.classDecl.name)
                        sb.append("[")
                        val entries1 = irType.getTypeArguments().entries
                        for ((index, pair) in entries1.withIndex()) {
                            val (_, typeArg) = pair
                            sb.append(printType(typeArg.second))
                            if (index != entries1.size - 1) {
                                sb.append(", ")
                            }
                        }
                        sb.append("]")
                        sb.toString()
                    }
                }

            is IrTypeParameter -> irType.name

            else -> throw NoWhenBranchMatchedException()
        }
        return typeStr
    }

    override fun IrClassDeclaration.printExtendList(superType: IrType?, implList: List<IrType>): String {
        val superAndIntf = if (superType != null) {
            listOf(superType) + implList
        } else {
            implList
        }
        if (superAndIntf.isEmpty()) {
            return ""
        }
        val sb = StringBuilder()
        for ((index, type) in superAndIntf.withIndex()) {
            if (index == 0) {
                sb.append(" extends ")
            } else {
                sb.append(" with ")
            }
            sb.append(printType(type))
        }
        return sb.toString()
    }

    override fun printTopLevelFunctionsAndProperties(program: IrProgram): String {
        TODO("Not yet implemented")
    }

    override fun print(element: IrClassDeclaration): String {
        val data = StringBuilder()
        visitClassDeclaration(element, data)
        return data.toString()
    }

    override fun visitClassDeclaration(clazz: IrClassDeclaration, data: StringBuilder) {
        data.append(indent)
        data.append(printIrClassType(clazz.classKind))
        data.append(clazz.name)
        val typeParameters = clazz.typeParameters
        if (typeParameters.isNotEmpty()) {
            data.append("[")
            for ((index, typeParameter) in typeParameters.withIndex()) {
                data.append(printType(typeParameter, printNullableAnnotation = false))
                if (index != typeParameters.lastIndex) {
                    data.append(", ")
                }
            }
            data.append("]")
        }
        data.append(clazz.printExtendList(clazz.superType, clazz.implementedTypes))
        data.append(" {\n")

        indentCount++
        super.visitClassDeclaration(clazz, data)
        indentCount--

        data.append(indent)
        data.append("}\n")
    }

//    override fun visitFunctionDeclaration(function: IrFunctionDeclaration, data: StringBuilder) {
//        elementStack.push(function)
//        /**
//         * Some version of Java's lexical analyzer is not greedy for matching multi line comments,
//         * so multi line comments in stubs need to be disabled.
//         */
//        if (function.isOverrideStub) {
//            data.append(indent)
//            data.append("// stub\n")
//            data.append(indent)
//            data.append("/*\n")
//        }
//        data.append(indent)
//        if (function.isOverride) {
//            data.append("override ")
//        }
//        data.append("def ")
//        data.append(function.name)
//        data.append("(")
//        visitParameterList(function.parameterList, data)
//        data.append("): ")
//        data.append(printType(function.returnType))
//        val body = function.body
//        if (body != null) {
//            data.append(" = \n")
//            indentCount++
//            visitBlock(body, data)
//            indentCount--
//        } else {
//            data.append("\n")
//        }
//        if (function.isOverrideStub) {
//            data.append(indent)
//            data.append("*/\n")
//        }
//        require(elementStack.pop() === function)
//    }

    /*override fun visitParameterList(parameterList: IrParameterList, data: StringBuilder) {
        val parameters = parameterList.parameters
        for ((index, parameter) in parameters.withIndex()) {
            data.append(parameter.name)
            data.append(": ")
            data.append(printType(parameter.type))
            if (index != parameters.lastIndex) {
                data.append(", ")
            }
        }
    }*/

    /*override fun visitProperty(property: IrPropertyDeclaration, data: StringBuilder) {
        TODO()
    }

    override fun visitBlock(block: IrBlock, data: StringBuilder) {
        val function = elementStack.peek() as IrFunctionDeclaration
        if (block.expressions.isEmpty()) {
            data.append(indent)
            data.append("???\n")
        } else {
            require(function.returnType === IrUnit || block.expressions.last() is IrReturnExpression)
        }
        for (expression in block.expressions) {
            data.append(indent)
            expression.accept(this, data)
            data.append(";\n")
        }
    }

    override fun visitNewExpression(newExpression: IrNew, data: StringBuilder) {
        data.append("new ")
        data.append(
            printType(
                newExpression.createType,
                printNullableAnnotation = false,
                noNullabilityAnnotation = true,
            )
        )
    }

    override fun visitFunctionCallExpression(functionCall: IrFunctionCall, data: StringBuilder) {
        TODO()
    }

    override fun visitReturnExpression(returnExpression: IrReturnExpression, data: StringBuilder) {
        data.append("return ")
        returnExpression.innerExpression?.accept(this, data)
    }

    override fun visitDefaultImplExpression(defaultImpl: IrDefaultImpl, data: StringBuilder) {
        data.append("null")
    }*/
}