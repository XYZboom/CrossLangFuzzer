package com.github.xyzboom.codesmith.printer.java

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.IrAccessModifier.*
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.types.IrClassType.*
import com.github.xyzboom.codesmith.ir.types.Variance.*
import com.github.xyzboom.codesmith.printer.AbstractIrClassPrinter

class IrJavaClassPrinter(indentCount: Int = 0): AbstractIrClassPrinter(indentCount) {

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
            INTERNAL -> ""
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
        with(element) {
            return "$indent${accessModifier.print()} ${classType.print()} $name {\n" +
                    "$indent}\n"
        }
    }
}