package com.github.xyzboom.codesmith.printer.java

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.expressions.*
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.types.IrClassType.*
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.printer.AbstractIrClassPrinter
import com.github.xyzboom.codesmith.printer.kt.KtIrClassPrinter

class JavaIrClassPrinter : AbstractIrClassPrinter() {
    companion object {
        private val builtInNames = buildMap {
            put(IrAny, "Object")
            put(IrNothing, "Void")
            put(IrUnit, "void")
        }

        const val IMPORTS =
            "import org.jetbrains.annotations.NotNull;\n" +
                    "import org.jetbrains.annotations.Nullable;\n"
        const val TOP_LEVEL_CONTAINER_CLASS_NAME = "JavaTopLevelContainer"
        const val FUNCTION_BODY_TODO = "throw new RuntimeException();"
    }

    private var printNullableAnnotation = false
    private var noNullableAnnotation = false

    override fun print(element: IrClassDeclaration): String {
        val data = StringBuilder(IMPORTS)
        visitClass(element, data)
        return data.toString()
    }

    override fun printTopLevelFunctionsAndProperties(program: IrProgram): String {
        val data = StringBuilder(IMPORTS)
        data.append("public final class $TOP_LEVEL_CONTAINER_CLASS_NAME {\n")
        indentCount++
        elementStack.push(program)
        for (function in program.functions.filter { it.language == Language.JAVA }) {
            visitFunction(function, data)
        }
        for (property in program.properties.filter { it.language == Language.JAVA }) {
            visitProperty(property, data)
        }
        indentCount--
        data.append("}")
        require(elementStack.pop() === program)
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
            is IrNullableType -> {
                val store = noNullableAnnotation
                noNullableAnnotation = true
                val result = printType(irType.innerType)
                noNullableAnnotation = store
                result
            }

            is IrBuiltInType -> return builtInNames[irType]
                ?: throw IllegalStateException("No such built-in type: $irType")

            is IrClassifier ->
                when (irType) {
                    is IrSimpleClassifier -> irType.classDecl.name
                    is IrParameterizedClassifier -> {
                        val sb = StringBuilder(irType.classDecl.name)
                        sb.append("<")
                        val entries1 = irType.getTypeArguments().entries
                        for ((index, pair) in entries1.withIndex()) {
                            val (_, typeArg) = pair
                            sb.append(printType(typeArg))
                            if (index != entries1.size - 1) {
                                sb.append(", ")
                            }
                        }
                        sb.append(">")
                        sb.toString()
                    }
                }

            is IrTypeParameter -> irType.name

            else -> throw NoWhenBranchMatchedException()
        }
        val annotationStr = if (printNullableAnnotation && !noNullableAnnotation) {
            if (irType is IrNullableType) "@Nullable "
            else "@NotNull "
        } else ""
        return "$annotationStr$typeStr"
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
        val typeParameters = clazz.typeParameters
        if (typeParameters.isNotEmpty()) {
            data.append("<")
            for ((index, typeParameter) in typeParameters.withIndex()) {
                data.append(printType(typeParameter))
                if (index != typeParameters.lastIndex) {
                    data.append(", ")
                }
            }
            data.append(">")
        }
        data.append(clazz.printExtendList(clazz.superType, clazz.implementedTypes))
        data.append(" {\n")

        indentCount++
        super.visitClass(clazz, data)
        indentCount--

        data.append(indent)
        data.append("}\n")
    }

    override fun visitFunction(function: IrFunctionDeclaration, data: StringBuilder) {
        val store = noNullableAnnotation
        noNullableAnnotation = function.printNullableAnnotations
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
            if (function.topLevel) {
                data.append("static ")
            } else {
                data.append("final ")
            }
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
            elementStack.push(function)
            visitBlock(body, data)
            require(elementStack.pop() === function)
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
        noNullableAnnotation = store
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

    override fun visitProperty(property: IrPropertyDeclaration, data: StringBuilder) {
        val propertyContainer: IrElement = elementStack.peek()

        fun buildGetterOrSetter(setter: Boolean) {
            data.append(indent)
            data.append("public ")
            if (property.isFinal) {
                if (property.topLevel) {
                    data.append("static ")
                } else {
                    data.append("final ")
                }
            }
            if (propertyContainer is IrClassDeclaration && propertyContainer.classType == INTERFACE) {
                data.append("default ")
            }
            if (setter) {
                data.append(printType(IrUnit))
            } else {
                data.append(printType(property.type))
            }
            data.append(" ")
            data.append(
                if (setter) {
                    "set"
                } else {
                    "get"
                }
            )
            data.append(property.name.replaceFirstChar { it.uppercaseChar() })
            data.append("(")
            if (setter) {
                data.append(printType(property.type))
                data.append(" value")
            }
            data.append(")")
            data.append(" {\n")
            indentCount++
            data.append(indent)
            data.append(FUNCTION_BODY_TODO)
            data.append("\n")
            indentCount--
            data.append(indent)
            data.append("}\n")
        }
        buildGetterOrSetter(false)
        if (!property.readonly) {
            buildGetterOrSetter(true)
        }
    }

    override fun visitBlock(block: IrBlock, data: StringBuilder) {
        val function = elementStack.peek() as IrFunctionDeclaration
        if (block.expressions.isEmpty()) {
            data.append(indent)
            data.append("throw new RuntimeException();\n")
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
        val store = printNullableAnnotation
        printNullableAnnotation = false
        data.append(printType(newExpression.createType))
        printNullableAnnotation = store
        data.append("()")
    }

    override fun visitFunctionCallExpression(functionCall: IrFunctionCall, data: StringBuilder) {
        val receiver = functionCall.receiver
        val target = functionCall.target
        if (receiver != null) {
            receiver.accept(this, data)
            data.append(".")
        } else if (target.topLevel) {
            if (target.language == Language.KOTLIN) {
                data.append(KtIrClassPrinter.TOP_LEVEL_CONTAINER_CLASS_NAME)
            } else {
                data.append(TOP_LEVEL_CONTAINER_CLASS_NAME)
            }
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
        data.append("null")
    }
}