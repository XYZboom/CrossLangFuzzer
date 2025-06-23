package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.Language.*
import com.github.xyzboom.codesmith.ir_old.IrElement
import com.github.xyzboom.codesmith.ir_old.IrParameterList
import com.github.xyzboom.codesmith.ir_old.IrProgram
import com.github.xyzboom.codesmith.ir_old.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir_old.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir_old.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir_old.expressions.*
import com.github.xyzboom.codesmith.ir_old.types.*
import com.github.xyzboom.codesmith.ir_old.types.IrClassType.*
import com.github.xyzboom.codesmith.ir_old.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir_old.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir_old.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir_old.types.builtin.IrUnit
import com.github.xyzboom.codesmith.printer.TypeContext
import com.github.xyzboom.codesmith.printer.TypeContext.*
import io.github.oshai.kotlinlogging.KotlinLogging

class JavaIrClassPrinter(
    private val majorLanguage: Language = KOTLIN
) : AbstractIrClassPrinter() {
    private val builtInNamesInTypeArgument = buildMap {
        put(IrAny, "Object")
        put(IrNothing, "Void")
        when (majorLanguage) {
            KOTLIN, GROOVY4, GROOVY5 -> put(IrUnit, "Void")
            SCALA -> put(IrUnit, "BoxedUnit")
            JAVA -> throw IllegalStateException("Could not set major language to Java!")
        }
        Unit
    }

    private val builtInTypeInParameter = buildMap {
        put(IrAny, "Object")
        put(IrNothing, "Void")
        when (majorLanguage) {
            KOTLIN, GROOVY4, GROOVY5 -> put(IrUnit, "Void")
            SCALA -> put(IrUnit, "BoxedUnit")
            JAVA -> throw IllegalStateException("Could not set major language to Java!")
        }
        Unit
    }

    private val imports = NULLABILITY_ANNOTATION_IMPORTS +
            when (majorLanguage) {
                KOTLIN, GROOVY4, GROOVY5 -> ""
                SCALA -> SCALA_RUNTIME_IMPORTS
                JAVA -> throw IllegalStateException("Could not set major language to Java!")
            }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val builtInNames = buildMap {
            put(IrAny, "Object")
            put(IrNothing, "Void")
            put(IrUnit, "void")
        }
        const val NULLABILITY_ANNOTATION_IMPORTS =
            "import org.jetbrains.annotations.NotNull;\n" +
                    "import org.jetbrains.annotations.Nullable;\n"
        const val SCALA_RUNTIME_IMPORTS =
            "import scala.runtime.BoxedUnit;\n"
        const val TOP_LEVEL_CONTAINER_CLASS_NAME = "JavaTopLevelContainer"
        const val FUNCTION_BODY_TODO = "throw new RuntimeException();"
    }

    override fun print(element: IrClassDeclaration): String {
        val data = StringBuilder(imports)
        visitClass(element, data)
        return data.toString()
    }

    override fun printTopLevelFunctionsAndProperties(program: IrProgram): String {
        val data = StringBuilder(imports)
        data.append("public final class $TOP_LEVEL_CONTAINER_CLASS_NAME {\n")
        indentCount++
        elementStack.push(program)
        for (function in program.functions.filter { it.language == JAVA }) {
            visitFunction(function, data)
        }
        for (property in program.properties.filter { it.language == JAVA }) {
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

    override fun printType(
        irType: IrType,
        typeContext: TypeContext,
        printNullableAnnotation: Boolean,
        noNullabilityAnnotation: Boolean
    ): String {
        val chooseTypeMap = when (typeContext) {
            TypeArgument -> builtInNamesInTypeArgument
            Parameter -> builtInTypeInParameter
            Other -> builtInNames
        }
        val typeStr = when (irType) {
            is IrNullableType -> {
                /**
                 * Since we are going to print [IrNullableType.innerType], so we close all nullability annotation
                 */
                val result = printType(
                    irType.innerType,
                    typeContext = typeContext,
                    printNullableAnnotation = printNullableAnnotation,
                    noNullabilityAnnotation = true,
                )
                result
            }

            is IrBuiltInType -> chooseTypeMap[irType]
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
                            sb.append(
                                printType(
                                    typeArg,
                                    typeContext = TypeArgument,
                                    printNullableAnnotation = printNullableAnnotation,
                                    noNullabilityAnnotation = noNullabilityAnnotation,
                                )
                            )
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
        val annotationStr = if (irType is IrNullableType) {
            "@Nullable"
        } else {
            "@NotNull"
        }
        val finalAnnotationStr = if (noNullabilityAnnotation) {
            ""
        } else if (!printNullableAnnotation) {
            "/*$annotationStr*/ "
        } else "$annotationStr "
        return "$finalAnnotationStr$typeStr"
    }

    override fun IrClassDeclaration.printExtendList(superType: IrType?, implList: List<IrType>): String {
        val sb = if (superType != null || implList.isNotEmpty()) {
            StringBuilder(" ")
        } else {
            StringBuilder()
        }
        if (superType != null) {
            sb.append("extends ")
            sb.append(printType(superType, printNullableAnnotation = false, noNullabilityAnnotation = true))
            sb.append(" ")
        }
        if (implList.isNotEmpty()) {
            if (classType != INTERFACE) {
                sb.append("implements ")
            } else {
                sb.append("extends ")
            }
            for ((index, type) in implList.withIndex()) {
                sb.append(printType(type, printNullableAnnotation = false, noNullabilityAnnotation = true))
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
        val typeParameters = clazz.typeParameters
        if (typeParameters.isNotEmpty()) {
            data.append("<")
            for ((index, typeParameter) in typeParameters.withIndex()) {
                data.append(printType(typeParameter, printNullableAnnotation = false))
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
        val functionContainer: IrElement = elementStack.peek()
        elementStack.push(function)
        /**
         * Some version of Java's lexical analyzer is not greedy for matching multi line comments,
         * so multi line comments in stubs need to be disabled.
         */
        val printNullableAnnotation = function.printNullableAnnotations || function.isOverrideStub
        if (function.isOverrideStub) {
            data.append(indent)
            data.append("// stub\n")
            data.append(indent)
            data.append("/*\n")
        }
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
        // IrUnit in Java is void if it is not type argument. It's Void otherwise.
        var anyOverrideReturnTypeIsTypeParameter = false
        function.traverseOverride {
            val returnType = it.returnType
            logger.trace { returnType.toString() }
            if (returnType is IrTypeParameter ||
                (returnType is IrNullableType && returnType.innerType is IrTypeParameter)
            ) {
                anyOverrideReturnTypeIsTypeParameter = true
            }
        }
        data.append(
            printType(
                function.returnType, printNullableAnnotation = printNullableAnnotation,
                typeContext = if (anyOverrideReturnTypeIsTypeParameter) {
                    TypeArgument
                } else {
                    Other
                }
            )
        )
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
        require(elementStack.pop() === function)
    }

    override fun visitParameterList(parameterList: IrParameterList, data: StringBuilder) {
        val parameters = parameterList.parameters
        val func = elementStack.peek() as IrFunctionDeclaration
        for ((index, parameter) in parameters.withIndex()) {
            data.append(
                printType(
                    parameter.type, Parameter,
                    printNullableAnnotation = func.printNullableAnnotations || func.isOverrideStub,
                )
            )
            data.append(" ")
            data.append(parameter.name)
            if (index != parameters.lastIndex) {
                data.append(", ")
            }
        }
    }

    override fun visitProperty(property: IrPropertyDeclaration, data: StringBuilder) {
        val propertyContainer: IrElement = elementStack.peek()
        val printNullableAnnotation = property.printNullableAnnotations

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
                data.append(printType(IrUnit, printNullableAnnotation = printNullableAnnotation))
            } else {
                data.append(printType(property.type, printNullableAnnotation = printNullableAnnotation))
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
                data.append(printType(property.type, printNullableAnnotation = printNullableAnnotation))
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
        data.append(
            printType(
                newExpression.createType,
                printNullableAnnotation = false,
                noNullabilityAnnotation = true,
            )
        )
        data.append("()")
    }

    override fun visitFunctionCallExpression(functionCall: IrFunctionCall, data: StringBuilder) {
        val receiver = functionCall.receiver
        val target = functionCall.target
        if (receiver != null) {
            receiver.accept(this, data)
            data.append(".")
        } else if (target.topLevel) {
            if (target.language == KOTLIN) {
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