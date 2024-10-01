package com.github.xyzboom.codesmith.printer.java

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.IrAccessModifier.*
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrConstructor
import com.github.xyzboom.codesmith.ir.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.types.IrClassType.*
import com.github.xyzboom.codesmith.ir.types.Variance.*
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
        val stringBuilder = if (classType == INTERFACE) {
            StringBuilder(" implements ")
        } else {
            StringBuilder(" extends ")
        }
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
            is IrConcreteType -> print()
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
            stringBuilder.append("$indent${accessModifier.print()} ${classType.print()} $name {\n")
        }
        super.visitClass(clazz, data)
        stringBuilder.append("$indent}\n")
    }

    override fun visitConstructor(constructor: IrConstructor, data: StringBuilder) {
        indentCount++
        stringBuilder.append(indent)
        stringBuilder.append(constructor.accessModifier.print())
        stringBuilder.append(" ")
        stringBuilder.append(constructor.containingDeclaration.name)
        stringBuilder.append("() {\n")
        indentCount++
        super.visitConstructor(constructor, data)
        indentCount--
        stringBuilder.append(indent)
        stringBuilder.append("}\n")
        indentCount--
    }

    override fun visitConstructorCallExpression(
        constructorCallExpression: IrConstructorCallExpression,
        data: StringBuilder
    ) {
        stringBuilder.append(indent)
        val last = elementStack.peek()
        if (last is IrConstructor) {
            val currentClass = elementStack.elementAt(elementStack.size - 2) as? IrClass
                ?: throw IllegalStateException()
            val clazz = constructorCallExpression.callTarget.containingDeclaration
            if (currentClass === clazz) {
                stringBuilder.append("this();\n")
            } else if (currentClass.superType?.declaration === clazz) {
                stringBuilder.append("super();\n")
            } else {
                throw IllegalStateException("A constructor call must call its super constructor or constructor in the same class")
            }
        }
        super.visitConstructorCallExpression(constructorCallExpression, data)
    }
}