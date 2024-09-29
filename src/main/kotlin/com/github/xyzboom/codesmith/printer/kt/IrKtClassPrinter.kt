package com.github.xyzboom.codesmith.printer.kt

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.IrAccessModifier.*
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.types.IrClassType.*
import com.github.xyzboom.codesmith.printer.AbstractIrClassPrinter

class IrKtClassPrinter(indentCount: Int = 0): AbstractIrClassPrinter(indentCount) {

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
        with(element) {
            return "$indent${accessModifier.print()} ${classType.print()} $name" +
                    "${printTypeParameters()} ${printExtendList()}\n"
        }
    }
}