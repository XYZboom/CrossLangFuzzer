package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.ClassKind.*
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.printer.TypeContext
import com.github.xyzboom.codesmith.printer.TypeContext.TypeParameterDeclaration

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
        visitClassDeclaration(element, data)
        return data.toString()
    }

    override fun printTopLevelFunctionsAndProperties(program: IrProgram): String {
        val data = StringBuilder()
        elementStack.push(program)
        for (function in program.functions.filter { it.language == Language.KOTLIN }) {
            visitFunctionDeclaration(function, data)
        }
        for (property in program.properties.filter { it.language == Language.KOTLIN }) {
            visitPropertyDeclaration(property, data)
        }
        require(elementStack.pop() === program)
        return data.toString()
    }

    override fun printTypeDirectly(
        irType: IrType,
        typeContext: TypeContext,
        noNullabilityAnnotation: Boolean
    ): String {
        return when (irType) {
            is IrNullableType -> return "${printType(irType.innerType)}?"
            is IrBuiltInType -> return builtInNames[irType]
                ?: throw IllegalStateException("No such built-in type: $irType")

            is IrClassifier ->
                when (irType) {
                    is IrSimpleClassifier -> irType.classDecl.name
                    is IrParameterizedClassifier -> {
                        val sb = StringBuilder(irType.classDecl.name)
                        sb.append("<")
                        // print type arg in the order of superType
                        val typeArgs = irType.getTypeArguments()
                        for ((index, typeParam) in irType.classDecl.typeParameters.withIndex()) {
                            val typeArg = typeArgs[IrTypeParameterName(typeParam.name)]!!.second
                            sb.append(printType(typeArg))
                            if (index != irType.classDecl.typeParameters.lastIndex) {
                                sb.append(", ")
                            }
                        }
                        sb.append(">")
                        sb.toString()
                    }
                }

            is IrTypeParameter -> if (typeContext != TypeParameterDeclaration) {
                irType.name
            } else {
                "${irType.name} : ${printType(irType.upperbound)}"
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

    override fun printIrClassType(irClassType: ClassKind): String {
        return when (irClassType) {
            ABSTRACT -> "abstract class "
            INTERFACE -> "interface "
            OPEN -> "open class "
            FINAL -> "class "
        }
    }

    override fun visitClassDeclaration(clazz: IrClassDeclaration, data: StringBuilder) {
        data.append(indent)
        data.append("public ")
        data.append(printIrClassType(clazz.classKind))
        data.append(clazz.name)
        val typeParameters = clazz.typeParameters
        if (typeParameters.isNotEmpty()) {
            data.append("<")
            for ((index, typeParameter) in typeParameters.withIndex()) {
                data.append(printType(typeParameter, typeContext = TypeContext.TypeParameterDeclaration))
                if (index != typeParameters.lastIndex) {
                    data.append(", ")
                }
            }
            data.append(">")
        }
        data.append(clazz.printExtendList(clazz.superType, clazz.implementedTypes))
        data.append(" {\n")

        indentCount++
        super.visitClassDeclaration(clazz, data)
        indentCount--

        data.append(indent)
        data.append("}\n")
    }

    override fun visitFunctionDeclaration(function: IrFunctionDeclaration, data: StringBuilder) {
        elementStack.push(function)
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
        if (function.typeParameters.isNotEmpty()) {
            data.append("<")
            for ((index, typeParam) in function.typeParameters.withIndex()) {
                data.append(typeParam.name)
                data.append(": ")
                data.append(printType(
                    typeParam.upperbound,
                    TypeContext.FunctionTypeParameterUpperBound
                ))
                if (index != function.typeParameters.lastIndex) {
                    data.append(", ")
                }
            }
            data.append(">")
        }
        data.append(function.name)
        data.append("(")
        visitParameterList(function.parameterList, data)
        data.append("): ")
        data.append(printType(function.returnType, typeContext = TypeContext.ReturnType))
        val body = function.body
        if (body != null) {
            data.append(" {\n")
            indentCount++
            elementStack.push(function)
            visitBlock(body, data)
            require(elementStack.pop() === function)
            indentCount--
            data.append(indent)
            data.append("}")
        }
        data.append("\n")
        if (function.isOverrideStub) {
            data.append(indent)
            data.append("*/\n")
        }
        require(elementStack.pop() === function)
    }

    override fun visitParameterList(parameterList: IrParameterList, data: StringBuilder) {
        val parameters = parameterList.parameters
        for ((index, parameter) in parameters.withIndex()) {
            data.append(parameter.name)
            data.append(": ")
            data.append(printType(parameter.type, typeContext = TypeContext.Parameter))
            if (index != parameters.lastIndex) {
                data.append(", ")
            }
        }
    }

    /*override fun visitProperty(property: IrPropertyDeclaration, data: StringBuilder) {
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
        if (property.topLevel) {
            data.append(" get()")
        }
        data.append(" = ")
        data.append("TODO()")
        data.append("\n")
    }*/

    override fun visitBlock(block: IrBlock, data: StringBuilder) {
        val function = elementStack.peek() as IrFunctionDeclaration
        if (block.expressions.isEmpty()) {
            data.append(indent)
            data.append("throw RuntimeException()\n")
        } else {
            require(function.returnType === IrUnit /*|| block.expressions.last() is IrReturnExpression*/)
        }
        for (expression in block.expressions) {
            data.append(indent)
            expression.accept(this, data)
            data.append("\n")
        }
    }

    /*override fun visitNewExpression(newExpression: IrNew, data: StringBuilder) {
        data.append(printType(newExpression.createType))
        data.append("()")
    }

    override fun visitFunctionCallExpression(functionCall: IrFunctionCall, data: StringBuilder) {
        val receiver = functionCall.receiver
        val target = functionCall.target
        if (receiver != null) {
            receiver.accept(this, data)
            data.append(".")
        } else if (target.language == Language.JAVA && target.topLevel) {
            data.append(JavaIrClassPrinter.TOP_LEVEL_CONTAINER_CLASS_NAME)
            data.append(".")
        }
        data.append(target.name)
        data.append("(")
        for ((index, argument) in functionCall.arguments.withIndex()) {
            argument.accept(this, data)
            if (index != functionCall.arguments.lastIndex) {
                data.append(", ")
            }
        }
        data.append(")")
    }

    override fun visitReturnExpression(returnExpression: IrReturnExpression, data: StringBuilder) {
        data.append("return ")
        returnExpression.innerExpression?.accept(this, data)
    }

    override fun visitDefaultImplExpression(defaultImpl: IrDefaultImpl, data: StringBuilder) {
        data.append("TODO()")
    }*/
}