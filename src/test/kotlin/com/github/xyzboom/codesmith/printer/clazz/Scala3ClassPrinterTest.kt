package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.expressions.IrNew
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class Scala3ClassPrinterTest {
    companion object {
        private val todoFunctionBody = "${" ".repeat(4)}???\n"
    }

    @Test
    fun testPrintSimpleClassWithSimpleFunction() {
        val printer = Scala3IrClassPrinter()
        val clazzName = "SimpleClassWithSimpleFunction"
        val funcName = "simple"
        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
        val func = IrFunctionDeclaration(funcName, clazz).apply {
            isFinal = true
            body = IrBlock()
        }
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = "class $clazzName {\n" +
                "  def $funcName(): Unit = \n" +
                todoFunctionBody +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintSimpleClassWithSimpleStubFunction() {
        val printer = Scala3IrClassPrinter()
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
        val expect = "class $clazzName {\n" +
                "  // stub\n" +
                "  /*\n" +
                "  def $funcName(): Unit = \n" +
                "    ???\n" +
                "  */\n" +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintSimpleClassWithFunctionHasParameter() {
        val printer = Scala3IrClassPrinter()
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
        val expect = "class SimpleClassWithFunctionHasParameter {\n" +
                "  def simple(arg0: Object, arg1: SimpleClassWithFunctionHasParameter): Unit = \n" +
                "    ???\n" +
                "}\n"
        assertEquals(expect, result)
    }

    //<editor-fold desc="Property">
    @Test
    @Disabled
    fun testPrintSimpleProperty() {
        val printer = Scala3IrClassPrinter()
        val clazzName = "SimpleClassWithSimpleFunction"
        val propertyTypeName = "PType"
        val propertyName = "simple"
        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
        val pClass = IrClassDeclaration(propertyTypeName, IrClassType.FINAL)
        val property = IrPropertyDeclaration(propertyName, clazz).apply {
            isFinal = true
            type = pClass.type
            readonly = true
        }
        clazz.properties.add(property)
        val result = printer.print(clazz)
        val expect = "public final class $clazzName {\n" +
                "    public final /*@NotNull*/ $propertyTypeName " +
                "get${propertyName.replaceFirstChar { it.uppercaseChar() }}() {\n" +
                todoFunctionBody +
                "    }\n" +
                "}\n"
        assertEquals(expect, result)
    }
    //</editor-fold>

    //<editor-fold desc="Expression">
    @Test
    @Disabled
    fun testPrintNewExpression() {
        val printer = Scala3IrClassPrinter()
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
        val expect = "public final class $clazzName {\n" +
                "    public final /*@NotNull*/ void $funcName() {\n" +
                "        new $clazzName();\n" +
                "    }\n" +
                "}\n"
        assertEquals(expect, result)
    }
}