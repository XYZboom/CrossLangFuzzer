package com.github.xyzboom.codesmith.printer.java

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrClassType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JavaIrClassPrinterTest {
    private val printer = JavaIrClassPrinter()
    @Test
    fun testPrintSimpleClassWithSimpleFunction() {
        val clazzName = "SimpleClassWithSimpleFunction"
        val funcName = "simple"
        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
        val func = IrFunctionDeclaration(funcName)
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = "public final class $clazzName {\n" +
                "\tpublic void $funcName() {\n" +
                "\t}\n" +
                "}\n"
        assertEquals(expect, result)
    }
}