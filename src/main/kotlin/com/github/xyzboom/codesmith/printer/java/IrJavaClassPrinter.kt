package com.github.xyzboom.codesmith.printer.java

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.IrAccessModifier.*
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrClassType.*
import com.github.xyzboom.codesmith.printer.AbstractIrClassPrinter

class IrJavaClassPrinter(indent: Int = 0): AbstractIrClassPrinter(indent) {

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

    override fun print(element: IrClass): String {
        with(element) {
            return "${"\t".repeat(indent)}${accessModifier.print()} ${classType.print()} $name {\n" +
                    "}\n"
        }
    }
}