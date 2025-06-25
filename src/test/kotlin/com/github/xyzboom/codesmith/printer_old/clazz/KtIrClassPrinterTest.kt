package com.github.xyzboom.codesmith.printer_old.clazz

import com.github.xyzboom.codesmith.ir_old.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir_old.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir_old.declarations.IrParameter
import com.github.xyzboom.codesmith.ir_old.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir_old.expressions.IrBlock
import com.github.xyzboom.codesmith.ir_old.expressions.IrNew
import com.github.xyzboom.codesmith.ir_old.types.IrClassType
import com.github.xyzboom.codesmith.ir_old.types.builtin.IrAny
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KtIrClassPrinterTest {
    companion object {
        private val todoFunctionBody = "${" ".repeat(8)}throw RuntimeException()\n"
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
                "    fun $funcName(): Unit {\n" +
                todoFunctionBody +
                "    }\n" +
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
                "    // stub\n" +
                "    /*\n" +
                "    override fun $funcName(): Unit {\n" +
                todoFunctionBody +
                "    }\n" +
                "    */\n" +
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
                "    fun $funcName(arg0: Any, arg1: $clazzName): Unit {\n" +
                todoFunctionBody +
                "    }\n" +
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
                "    val $propertyName: Any = $todoPropertyInitExpr\n" +
                "}\n"
        assertEquals(expect, result)
    }
    //</editor-fold>

    //<editor-fold desc="Expression">
    @Test
    fun testPrintNewExpression() {
        val printer = KtIrClassPrinter()
        val clazzName = "SimpleClassWithSimpleFunction"
        val funcName = "simple"
        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
        val func = IrFunctionDeclaration(funcName, clazz).apply {
            isFinal = true
            body = IrBlock().apply {
                expressions.add(IrNew.create(clazz.type))
            }
        }
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = "public class $clazzName {\n" +
                "    fun $funcName(): Unit {\n" +
                "        $clazzName()\n" +
                "    }\n" +
                "}\n"
        assertEquals(expect, result)
    }
    //</editor-fold>

}