package com.github.xyzboom.codesmith.newprinter.clazz

import com.github.xyzboom.codesmith.bf.generated.IMemberMethodNode
import com.github.xyzboom.codesmith.newir.ClassKind
import com.github.xyzboom.codesmith.newir.ClassKind.*
import com.github.xyzboom.codesmith.newir.decl.IrClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.Taggable
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpecHolder
import com.squareup.kotlinpoet.TypeVariableName
import java.util.Stack

class KotlinIrClassPrinter : AbstractIrClassPrinter<FileSpec.Builder>() {

    //    companion object {
//        private val builtInNames = buildMap {
//            put(IrAny, "Any")
//            put(IrNothing, "Nothing")
//            put(IrUnit, "Unit")
//        }
//
//        /**
//         * For java interaction
//         */
//        const val TOP_LEVEL_CONTAINER_CLASS_NAME = "MainKt"
//    }
//
    private val declBuilders = Stack<Taggable.Builder<*>>()
    private val lastTypeContainer: TypeSpecHolder.Builder<*> =
        declBuilders.first { it is TypeSpecHolder.Builder<*> } as TypeSpecHolder.Builder<*>

    private inline fun <T : Taggable.Builder<*>> buildContext(builder: T, block: T.() -> Unit) {
        declBuilders.push(builder)
        builder.block()
        require(declBuilders.pop() === builder)
    }

    override fun print(element: IrClassDeclaration): String {
        val fileBuilder = FileSpec.builder("", element.name)
        declBuilders.push(fileBuilder)
        visitClassNode(element, fileBuilder)
        require(declBuilders.pop() === fileBuilder)
        val typeSpec = fileBuilder.build()
        return typeSpec.toString()
    }

    /*override fun printTopLevelFunctionsAndProperties(program: IrProgram): String {
        val data = StringBuilder()
        nodeStack.push(program)
        for (function in program.functions.filter { it.language == Language.KOTLIN }) {
            visitFunction(function, data)
        }
        for (property in program.properties.filter { it.language == Language.KOTLIN }) {
            visitProperty(property, data)
        }
        require(nodeStack.pop() === program)
        return data.toString()
    }

    override fun printType(
        irType: IrType,
        typeContext: TypeContext,
        printNullableAnnotation: Boolean,
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
    }*/

    override fun printClassKind(classKind: ClassKind): String {
        return when (classKind) {
            ABSTRACT -> "abstract class "
            INTERFACE -> "interface "
            OPEN -> "open class "
            FINAL -> "class "
        }
    }

    override fun visitClassNode(clazz: IrClassDeclaration, data: FileSpec.Builder) {
        val builder = if (clazz.classKind == INTERFACE) {
            TypeSpec.interfaceBuilder(clazz.name)
        } else {
            TypeSpec.classBuilder(clazz.name)
        }
        buildContext(builder) {
            addModifiers(KModifier.PUBLIC)
            when (clazz.classKind) {
                ABSTRACT -> addModifiers(KModifier.ABSTRACT)
                INTERFACE -> {} // do nothing
                OPEN -> addModifiers(KModifier.OPEN)
                FINAL -> addModifiers(KModifier.FINAL)
            }
            for (typeParam in clazz.typeParameters) {
                addTypeVariable(TypeVariableName(typeParam.name))
            }
            super.visitClassNode(clazz, data)
        }
        lastTypeContainer.addType(builder.build())
    }

    val comment1 = """override fun visitMemberMethodNode(node: IMemberMethodNode, data: FileSpec.Builder) {

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
            nodeStack.push(function)
            visitBlock(body, data)
            require(nodeStack.pop() === function)
            indentCount--
            data.append(indent)
            data.append("}")
        }
        data.append("\n")
        if (function.isOverrideStub) {
            data.append(indent)
            data.append("*/\n")
        }
    }"""

    val comment = """

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
        if (property.topLevel) {
            data.append(" get()")
        }
        data.append(" = ")
        data.append("TODO()")
        data.append("\n")
    }

    override fun visitBlock(block: IrBlock, data: StringBuilder) {
        val function = nodeStack.peek() as IrFunctionDeclaration
        if (block.expressions.isEmpty()) {
            data.append(indent)
            data.append("throw RuntimeException()\n")
        } else {
            require(function.returnType === IrUnit || block.expressions.last() is IrReturnExpression)
        }
        for (expression in block.expressions) {
            data.append(indent)
            expression.accept(this, data)
            data.append("\n")
        }
    }

    override fun visitNewExpression(newExpression: IrNew, data: StringBuilder) {
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
    }"""
}