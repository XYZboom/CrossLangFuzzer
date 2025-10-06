package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.Language.*
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.ClassKind.*
import com.github.xyzboom.codesmith.ir.declarations.traverseOverride
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.printer.TypeContext
import com.github.xyzboom.codesmith.printer.TypeContext.*
import io.github.oshai.kotlinlogging.KotlinLogging

class JavaIrClassPrinter(
    private val majorLanguage: Language = KOTLIN,
    printStub: Boolean = true
) : AbstractIrClassPrinter(printStub = printStub) {
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

    /**
     * value: The binary value of 1 corresponding to the position from the low position indicates that
     * the parameter of the index is a type parameter
     * Example: 0b1011 for function: f(a, b, c, d) means a, b and d is type parameter
     *          (in current or parent class)
     */
    private val functionParameterTypeHasTypeParameter = mutableMapOf<IrFunctionDeclaration, Int>()

    override fun print(element: IrClassDeclaration): String {
        val data = StringBuilder(imports)
        visitClassDeclaration(element, data)
        return data.toString()
    }

    override fun printTopLevelFunctionsAndProperties(program: IrProgram): String {
        val data = StringBuilder(imports)
        data.append("public final class $TOP_LEVEL_CONTAINER_CLASS_NAME {\n")
        indentCount++
        elementStack.push(program)
        for (function in program.functions.filter { it.language == JAVA }) {
            visitFunctionDeclaration(function, data)
        }
        for (property in program.properties.filter { it.language == JAVA }) {
            visitPropertyDeclaration(property, data)
        }
        indentCount--
        data.append("}")
        require(elementStack.pop() === program)
        return data.toString()
    }

    override fun printIrClassType(irClassType: ClassKind): String {
        return when (irClassType) {
            ABSTRACT -> "abstract class "
            INTERFACE -> "interface "
            OPEN -> "class "
            FINAL -> "final class "
        }
    }

    override fun printTypeDirectly(
        irType: IrType,
        typeContext: TypeContext,
        noNullabilityAnnotation: Boolean
    ): String {
        val chooseTypeMap = when (typeContext) {
            TypeArgument, TypeArgumentInReturnType -> builtInNamesInTypeArgument
            Parameter -> builtInTypeInParameter
            TypeParameterDeclaration, Other -> builtInNames
            else -> builtInNames
        }
        val typeStr = when (irType) {
            is IrNullableType -> {
                /**
                 * Since we are going to print [IrNullableType.innerType], so we close all nullability annotation
                 */
                val result = printType(
                    irType.innerType,
                    typeContext = typeContext,
                    noNullabilityAnnotation = true,
                )
                result
            }

            is IrPlatformType -> {
                /**
                 * Since we are going to print [IrPlatformType.innerType], so we close all nullability annotation
                 */
                val result = printType(
                    /**
                     * ```kt
                     * class A<T: Any?> {
                     *     fun foo(t: T!)
                     * }
                     * ```
                     * `A<Any>::foo::t` is `Any`, not `Any!`
                     * `A<Any?>::foo::t` is `Any?!` and is `Any!` in Java but is `Any?` in Kotlin.
                     */
                    irType.innerType.notNullType,
                    typeContext = typeContext,
                    noNullabilityAnnotation = true,
                )
                result
            }

            is IrBuiltInType -> chooseTypeMap[irType]
                ?: throw IllegalStateException("No such built-in type: $irType")

            is IrSimpleClassifier -> irType.classDecl.name
            is IrParameterizedClassifier -> {
                val sb = StringBuilder(irType.classDecl.name)
                sb.append("<")
                // print type arg in the order of superType
                val typeArgs = irType.getTypeArguments()
                for ((index, typeParam) in irType.classDecl.typeParameters.withIndex()) {
                    val typeArg = typeArgs[IrTypeParameterName(typeParam.name)]!!.second
                    sb.append(
                        printType(
                            typeArg,
                            typeContext = TypeArgument
                        )
                    )
                    if (index != irType.classDecl.typeParameters.lastIndex) {
                        sb.append(", ")
                    }
                }
                sb.append(">")
                sb.toString()
            }

            is IrTypeParameter -> irType.name

            is IrDefinitelyNotNullType -> printType(irType.innerType, noNullabilityAnnotation = true)

            else -> throw NoWhenBranchMatchedException()
        }
        val annotationStr = if (irType is IrNullableType) {
            "@Nullable"
        } else {
            "@NotNull"
        }
        val finalAnnotationStr =
            if (noNullabilityAnnotation || typeStr == "void" ||
                (irType is IrTypeParameter && irType.deepUpperboundNullable()) ||
                irType is IrPlatformType
            ) {
                ""
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
            sb.append(printType(superType, noNullabilityAnnotation = true))
            sb.append(" ")
        }
        if (implList.isNotEmpty()) {
            if (classKind != INTERFACE) {
                sb.append("implements ")
            } else {
                sb.append("extends ")
            }
            for ((index, type) in implList.withIndex()) {
                sb.append(printType(type, noNullabilityAnnotation = true))
                if (index != implList.lastIndex) {
                    sb.append(", ")
                }
            }
        }
        return sb.toString()
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
                data.append(
                    printType(
                        typeParameter, typeContext = TypeParameterDeclaration,
                        noNullabilityAnnotation = true
                    )
                )
                data.append(" extends ")
                data.append(
                    printType(typeParameter.upperbound)
                )
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
        if (function.isOverrideStub && !printStub) {
            return
        }
        val functionContainer: IrElement = elementStack.peek()
        elementStack.push(function)
        /**
         * Some version of Java's lexical analyzer is not greedy for matching multi line comments,
         * so multi line comments in stubs need to be disabled.
         */
        var printNullableAnnotation = function.printNullableAnnotations || function.isOverrideStub
        if (function.isOverrideStub) {
            data.append(indent)
            data.append("// stub\n")
            data.append(indent)
            data.append("/*\n")
        }
        if (function.isOverride) {
            data.append(indent)
            data.append("@Override\n")
        }
        data.append(indent)
        data.append("public ")
        if (function.body == null) {
            data.append("abstract ")
        } else {
            if (functionContainer is IrClassDeclaration && functionContainer.classKind == INTERFACE) {
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
        if (function.typeParameters.isNotEmpty()) {
            data.append("<")
            for ((index, typeParam) in function.typeParameters.withIndex()) {
                data.append(typeParam.name)
                data.append(" extends ")
                data.append(
                    printType(
                        typeParam.upperbound,
                        FunctionTypeParameterUpperBound
                    )
                )
                if (index != function.typeParameters.lastIndex) {
                    data.append(", ")
                }
            }
            data.append(">")
        }
        val myReturnTypeIsTypeParameter = function.run {
            returnType is IrTypeParameter ||
                    (returnType is IrNullableType && (returnType as IrNullableType).innerType is IrTypeParameter)
        }
        // IrUnit in Java is void if it is not type argument. It's Void otherwise.
        var anyOverrideReturnTypeIsTypeParameter = false
        // see this.functionParameterTypeHasTypeParameter
        var anyOverrideParameterIsTypeParameter = 0
        function.traverseOverride {
            val returnType = it.returnType
            logger.trace { returnType.toString() }
            if (returnType is IrTypeParameter ||
                (returnType is IrNullableType && returnType.innerType is IrTypeParameter)
            ) {
                anyOverrideReturnTypeIsTypeParameter = true
            }
            var current = 1
            for (param in it.parameterList.parameters) {
                val paramType = param.type
                if (paramType is IrTypeParameter ||
                    (paramType is IrNullableType && paramType.innerType is IrTypeParameter)
                ) {
                    anyOverrideParameterIsTypeParameter = anyOverrideParameterIsTypeParameter or current
                }
                current = current shl 1 // current << 1 (shl is shift left in Kotlin)
            }
        }
        functionParameterTypeHasTypeParameter[function] = anyOverrideParameterIsTypeParameter
        printNullableAnnotation = printNullableAnnotation ||
                anyOverrideReturnTypeIsTypeParameter || myReturnTypeIsTypeParameter
        data.append(
            printType(
                function.returnType,
                /**
                 * ```java
                 * class A<T extends @NotNull Object> {
                 *     public /*@Nullable*/ T func() {}
                 *     //                   ^
                 *     // type parameter T here is not a platform type! Instead, it is a NotNull type.
                 * }
                 * ```
                 */
                typeContext = ReturnType,
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
        // see this.functionParameterTypeHasTypeParameter
        var current = 1
        val anyOverrideParameterIsTypeParameter = functionParameterTypeHasTypeParameter[func]!!
        for ((index, parameter) in parameters.withIndex()) {
            val parameterType = parameter.type
            data.append(
                printType(
                    parameterType, Parameter,
                    forcePrintNullableAnnotationIfIsTypeParameter = func.override.isEmpty()
//                        (anyOverrideParameterIsTypeParameter and current) != 0
                )
            )
            data.append(" ")
            data.append(parameter.name)
            if (index != parameters.lastIndex) {
                data.append(", ")
            }
            current = current shl 1
        }
    }

    /*override fun visitProperty(property: IrPropertyDeclaration, data: StringBuilder) {
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
    }*/

    override fun visitBlock(block: IrBlock, data: StringBuilder) {
        val function = elementStack.peek() as IrFunctionDeclaration
        if (block.expressions.isEmpty()) {
            data.append(indent)
            data.append("throw new RuntimeException();\n")
        } else {
            require(function.returnType === IrUnit /*|| block.expressions.last() is IrReturnExpression*/)
        }
        for (expression in block.expressions) {
            data.append(indent)
            expression.accept(this, data)
            data.append(";\n")
        }
    }

    /*override fun visitNewExpression(newExpression: IrNew, data: StringBuilder) {
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
    }*/
}