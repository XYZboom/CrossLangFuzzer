package com.github.xyzboom.codesmith.printer.java

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.IrAccessModifier.*
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrConstructor
import com.github.xyzboom.codesmith.ir.declarations.IrFile
import com.github.xyzboom.codesmith.ir.declarations.IrValueParameter
import com.github.xyzboom.codesmith.ir.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.types.IrClassType.*
import com.github.xyzboom.codesmith.ir.types.Variance.*
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltinTypes
import com.github.xyzboom.codesmith.printer.AbstractIrClassPrinter

class IrJavaClassPrinter(indentCount: Int = 0): AbstractIrClassPrinter(indentCount) {
    private val stringBuilder = StringBuilder()

    override fun IrClassType.print(): String {
        return when (this) {
            ABSTRACT -> "abstract class"
            INTERFACE -> "interface"
            OPEN -> "class"
            FINAL -> "final class"
        }
    }

    override fun IrAccessModifier.print(): String {
        return when (this) {
            PUBLIC -> "public"
            INTERNAL -> "/*default*/"
            PROTECTED -> "protected"
            PRIVATE -> "private"
        }
    }

    override fun IrClass.printExtendList(): String {
        if (superType == null && implementedTypes.isEmpty()) return ""
        val stringBuilder = StringBuilder(" extends ")
        if (superType != null) {
            stringBuilder.append(superType!!.print())
            if (implementedTypes.isNotEmpty()) {
                stringBuilder.append(" implements ")
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
            is IrStarProjection -> "?"
            is IrConcreteType -> printIrConcreteType(this)
            is IrTypeParameter -> name
            is IrTypeProjection -> {
                val varianceString = when (variance) {
                    INVARIANT -> ""
                    IN_VARIANCE -> "? super"
                    OUT_VARIANCE -> "? extends"
                }
                "$varianceString ${type.print()}"
            }
        }
    }

    override fun IrClass.printTypeParameters(): String {
        if (typeParameters.isEmpty()) return ""
        return typeParameters.joinToString(", ") { "${it.name} extends ${it.upperBound}" }
    }

    override fun print(element: IrClass): String {
        stringBuilder.clear()
        visitClass(element, stringBuilder)
        return stringBuilder.toString()
    }

    override fun visitClass(clazz: IrClass, data: StringBuilder) {
        with(clazz) {
            val containingDeclaration = clazz.containingDeclaration
            if (accessModifier == PUBLIC && containingDeclaration is IrFile) {
                data.append(
                    "// FILE: ${clazz.name}.java\n" +
                            "package ${containingDeclaration.containingPackage.fullName};\n"
                )
            }
            data.append(
                "$indent${accessModifier.print()} ${classType.print()} $name" +
                        "${printExtendList()} {\n"
            )
        }
        super.visitClass(clazz, data)
        data.append("$indent}\n")
    }

    override fun visitConstructor(constructor: IrConstructor, data: StringBuilder) {
        indentCount++
        data.append(indent)
        data.append(constructor.accessModifier.print())
        data.append(" ")
        data.append(constructor.containingDeclaration.name)
        data.append("(")
        for ((i, valueParam) in constructor.valueParameters.withIndex()) {
            valueParam.accept(this, data)
            if (i != constructor.valueParameters.lastIndex) {
                data.append(", ")
            }
        }
        data.append(") {\n")
        indentCount++
        constructor.superCall.accept(this, data)
        indentCount--
        data.append(indent)
        data.append("}\n")
        indentCount--
    }

    override fun printIrConcreteType(concreteType: IrConcreteType): String {
        if (concreteType.name == IrBuiltinTypes.ANY.name) {
            return "Object"
        }
        return super.printIrConcreteType(concreteType)
    }

    override fun visitValueParameter(
        valueParameter: IrValueParameter,
        data: StringBuilder
    ) {
        data.append(valueParameter.type.print())
        data.append(" ")
        data.append(valueParameter.name)
        super.visitValueParameter(valueParameter, data)
    }

    override fun visitConstructorCallExpression(
        constructorCallExpression: IrConstructorCallExpression,
        data: StringBuilder
    ) {
        data.append(indent)
        val last = elementStack.peek()
        if (last is IrConstructor) {
            val currentClass = elementStack.elementAt(elementStack.size - 2) as? IrClass
                ?: throw IllegalStateException()
            val clazz = constructorCallExpression.callTarget.containingDeclaration
            if (currentClass === clazz) {
                data.append("this();")
            } else if (currentClass.superType?.declaration === clazz) {
                data.append("super();")
            } else {
                throw IllegalStateException("A constructor call must call its super constructor or constructor in the same class")
            }
        }
        data.append("\n")
        super.visitConstructorCallExpression(constructorCallExpression, data)
    }
}