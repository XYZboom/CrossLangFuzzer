package com.github.xyzboom.codesmith.printer.kt

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KtIrClassPrinterTest {
    companion object {
        private val todoFunctionBody = "\t\tthrow RuntimeException()\n"
        private val todoPropertyInitExpr = "TODO()"
    }

    //<editor-fold desc="Function">
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
                todoFunctionBody +
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
                "\t// stub\n" +
                "\t/*\n" +
                "\toverride fun $funcName(): Unit {\n" +
                todoFunctionBody +
                "\t}\n" +
                "\t*/\n" +
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
                todoFunctionBody +
                "\t}\n" +
                "}\n"
        assertEquals(expect, result)
    }
    //</editor-fold>

    //<editor-fold desc="Property">
    @Test
    fun testPrintSimpleProperty() {
        val printer = KtIrClassPrinter()
        val clazzName = "SimpleClassWithSimpleFunction"
        val propertyName = "simple"
        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
        val property = IrPropertyDeclaration(propertyName, clazz).apply {
            isFinal = true
            type = IrAny
            readonly = true
        }
        clazz.properties.add(property)
        val result = printer.print(clazz)
        val expect = "public class $clazzName {\n" +
                "\tval $propertyName: Any = $todoPropertyInitExpr\n" +
                "}\n"
        assertEquals(expect, result)
    }
    //</editor-fold>
}