package com.github.xyzboom.codesmith.printer.kt

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.printer.java.JavaIrClassPrinter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KtIrClassPrinterTest {
    @Test
    fun testPrintSimpleClassWithSimpleFunction() {
        val printer = KtIrClassPrinter()
        val clazzName = "SimpleClassWithSimpleFunction"
        val funcName = "simple"
        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
        val func = IrFunctionDeclaration(funcName, clazz).apply {
            isFinal = true
            body = IrBlock()
        }
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = "public class $clazzName {\n" +
                "\tfun $funcName(): Unit {\n" +
                "\t}\n" +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintSimpleClassWithSimpleStubFunction() {
        val printer = KtIrClassPrinter()
        val clazzName = "SimpleClassWithSimpleFunction"
        val funcName = "simple"
        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
        val func = IrFunctionDeclaration(funcName, clazz).apply {
            isFinal = true
            isOverride = true
            isOverrideStub = true
            body = IrBlock()
        }
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = "public class $clazzName {\n" +
                "\t// stub\n"+
                "\t/*\n"+
                "\toverride fun $funcName(): Unit {\n" +
                "\t}\n" +
                "\t*/\n"+
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintSimpleClassWithFunctionHasParameter() {
        val printer = KtIrClassPrinter()
        val clazzName = "SimpleClassWithFunctionHasParameter"
        val funcName = "simple"
        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
        val func = IrFunctionDeclaration(funcName, clazz).apply {
            isFinal = true
            body = IrBlock()
            parameterList.parameters.add(IrParameter("arg0", IrAny))
            parameterList.parameters.add(IrParameter("arg1", clazz.type))
        }
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = "public class $clazzName {\n" +
                "\tfun $funcName(arg0: Any, arg1: $clazzName): Unit {\n" +
                "\t}\n" +
                "}\n"
        assertEquals(expect, result)
    }
}