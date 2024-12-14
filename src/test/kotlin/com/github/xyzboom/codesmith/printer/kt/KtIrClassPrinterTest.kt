package com.github.xyzboom.codesmith.printer.kt

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KtIrClassPrinterTest {
    private val printer = KtIrClassPrinter()
    @Test
    fun testPrintSimpleClassWithSimpleFunction() {
        val clazzName = "SimpleClassWithSimpleFunction"
        val funcName = "simple"
        val clazz = IrClassDeclaration(clazzName)
        val func = IrFunctionDeclaration(funcName)
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = "public class $clazzName {\n" +
                "\tfun $funcName(): Unit {\n" +
                "\t}\n" +
                "}\n"
        assertEquals(expect, result)
    }
}