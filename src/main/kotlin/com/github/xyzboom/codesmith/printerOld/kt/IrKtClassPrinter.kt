package com.github.xyzboom.codesmith.printerOld.kt

import com.github.xyzboom.codesmith.irOld.IrAccessModifier
import com.github.xyzboom.codesmith.irOld.IrAccessModifier.*
import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.declarations.IrConstructor
import com.github.xyzboom.codesmith.irOld.declarations.IrFunction
import com.github.xyzboom.codesmith.irOld.declarations.IrValueParameter
import com.github.xyzboom.codesmith.irOld.expressions.*
import com.github.xyzboom.codesmith.irOld.types.*
import com.github.xyzboom.codesmith.irOld.types.IrClassType.*
import com.github.xyzboom.codesmith.printerOld.AbstractIrClassPrinter

class IrKtClassPrinter(indentCount: Int = 0): AbstractIrClassPrinter(indentCount) {

    private val stringBuilder = StringBuilder()

    override fun IrClassType.print(): String {
        return when (this) {
            ABSTRACT -> "abstract class"
            INTERFACE -> "interface"
            OPEN -> "open class"
            FINAL -> "class"
        }
    }

    override fun IrAccessModifier.print(): String {
        return when (this) {
            PUBLIC -> "public"
            INTERNAL -> "internal"
            PROTECTED -> "protected"
            PRIVATE -> "private"
        }
    }

    override fun IrClass.printExtendList(): String {
        if (superType == null && implementedTypes.isEmpty()) return ""
        val stringBuilder = StringBuilder(": ")
        if (superType != null) {
            stringBuilder.append(superType!!.print())
            if (implementedTypes.isNotEmpty()) {
                stringBuilder.append(", ")
            }
        }
        for ((index, intf) in implementedTypes.withIndex()) {
            stringBuilder.append(intf.print())
            if (index != implementedTypes.lastIndex) {
                stringBuilder.append(", ")
            }
        }
        return stringBuilder.toString()
    }

    override fun IrTypeArgument.print(): String {
        return when (this) {
            is IrStarProjection -> "*"
            is IrConcreteType -> printIrConcreteType(this)
            is IrTypeParameter -> name
            is IrTypeProjection -> "${variance.label} ${type.print()}"
        }
    }

    override fun printIrConcreteType(concreteType: IrConcreteType): String {
        with(concreteType) {
            return if (this is IrFunctionTypeMarker) {
                val stringBuilder = StringBuilder("(")
                if (arguments.size != 1) {
                    for (i in 0 until arguments.size - 1) {
                        val arg = arguments[i]
                        stringBuilder.append(arg.print())
                        if (i != arguments.size - 2) {
                            stringBuilder.append(", ")
                        }
                    }
                }
                stringBuilder.append(") -> ")
                stringBuilder.append(arguments.last().print()).toString()
            } else {
                super.printIrConcreteType(concreteType)
            }
        }
    }

    override fun IrClass.printTypeParameters(): String {
        if (typeParameters.isEmpty()) return ""
        return typeParameters.joinToString(", ") { "${it.name}: ${it.upperBound}" }
    }

    override fun print(element: IrClass): String {
        val data = stringBuilder
        data.clear()
        with(element) {
            data.append(
                "$indent${accessModifier.print()} ${classType.print()} $name" +
                        "${printTypeParameters()} ${printExtendList()} {\n"
            )
        }
        visitClass(element, data)
        val noArg = element.declarations.filterIsInstance<IrConstructor>().filter { it.valueParameters.isEmpty() }
        if (element.classType != INTERFACE && noArg.isEmpty()) {
            val specialConstructor = element.specialConstructor
            if (specialConstructor != null) {
                data.append(
                    "${indent}\t${specialConstructor.accessModifier.print()} constructor(): super() {" +
                            "\n$indent\t}\n"
                )
            } else {
                data.append(
                    "${indent}\tpublic constructor(): super() {" +
                            "\n$indent\t}\n"
                )
            }
        }
        data.append(indent)
        data.append("}\n")
        return data.toString()
    }

    override fun visitConstructor(constructor: IrConstructor, data: StringBuilder) {
        indentCount++
        elementStack.push(constructor)
        data.append(indent)
        data.append(constructor.accessModifier.print())
        data.append(" constructor(")
        for ((i, valueParam) in constructor.valueParameters.withIndex()) {
            valueParam.accept(this, data)
            if (i != constructor.valueParameters.lastIndex) {
                data.append(", ")
            }
        }
        data.append("): ")
        constructor.superCall.accept(this, data)
        data.append(" {")
        data.append("\n")
        data.append(indent)
        data.append("}\n")
        assert(elementStack.pop() === constructor)
        indentCount--
    }

    override fun visitFunction(function: IrFunction, data: StringBuilder) {
        indentCount++
        elementStack.push(function)
        data.append(indent)
        data.append(function.accessModifier.print())
        data.append(" fun ")
        data.append(function.name)
        data.append("(")
        for ((i, valueParam) in function.valueParameters.withIndex()) {
            valueParam.accept(this, data)
            if (i != function.valueParameters.lastIndex) {
                data.append(", ")
            }
        }
        data.append("): ")
        data.append(printIrConcreteType(function.returnType))
        data.append(" {\n")
        for ((i, expression) in function.expressions.withIndex()) {
            data.append("\t")
            data.append(indent)
            if (i == function.expressions.lastIndex) {
                data.append("return ")
            }
            expression.accept(this, data)
            if (i != function.expressions.lastIndex) {
                data.append("\n")
            }
        }
        data.append("\n")
        data.append(indent)
        data.append("}\n")
        assert(elementStack.pop() === function)
        indentCount--
    }

    override fun visitValueParameter(
        valueParameter: IrValueParameter,
        data: StringBuilder
    ) {
        data.append(valueParameter.name)
        data.append(": ")
        data.append(valueParameter.type.print())
        super.visitValueParameter(valueParameter, data)
    }

    override fun visitConstructorCallExpression(
        constructorCallExpression: IrConstructorCallExpression,
        data: StringBuilder
    ) {
        val last = elementStack.peek()
        if (last is IrConstructor) {
            val currentClass = elementStack.elementAt(elementStack.size - 2) as? IrClass
                ?: throw IllegalStateException()
            val clazz = constructorCallExpression.callTarget.containingDeclaration
            if (currentClass === clazz) {
                data.append("this(")
            } else if (currentClass.superType?.declaration === clazz) {
                data.append("super(")
            } else {
                throw IllegalStateException("A constructor call must call its super constructor or constructor in the same class")
            }
        } else {
            data.append(printIrConcreteType(constructorCallExpression.callTarget.containingClass.type))
            data.append("(")
        }
        for ((i, valueArgument) in constructorCallExpression.valueArguments.withIndex()) {
            val before = data.length
            elementStack.push(constructorCallExpression)
            valueArgument.accept(this, data)
            assert(elementStack.pop() === constructorCallExpression)
            val after = data.length
            if (before == after) {
                throw AssertionError()
            }
            if (i != constructorCallExpression.valueArguments.lastIndex) {
                data.append(", ")
            }
        }
        data.append(")")
        elementStack.push(constructorCallExpression)
        super.visitConstructorCallExpression(constructorCallExpression, data)
        assert(elementStack.pop() === constructorCallExpression)
    }

    override fun visitAnonymousObject(anonymousObject: IrAnonymousObject, data: StringBuilder) {
        data.append("(object: ")
        val superClass = anonymousObject.superClass
        data.append(printIrConcreteType(superClass.type))
        if (superClass.classType != INTERFACE) {
            data.append("()")
        }
        // add convert to call private members
        data.append("{} as ")
        data.append(printIrConcreteType(superClass.type))
        data.append(")")
        super.visitAnonymousObject(anonymousObject, data)
    }

    override fun visitTodoExpression(todoExpression: IrTodoExpression, data: StringBuilder) {
        data.append("TODO()")
        super.visitTodoExpression(todoExpression, data)
    }

    override fun visitConstantExpression(constantExpression: IrConstantExpression, data: StringBuilder) {
        when (constantExpression) {
            IrConstantExpression.True -> data.append("true")
            IrConstantExpression.False -> data.append("false")
            is IrConstantExpression.Number -> data.append(constantExpression.value)
        }
        super.visitConstantExpression(constantExpression, data)
    }
}