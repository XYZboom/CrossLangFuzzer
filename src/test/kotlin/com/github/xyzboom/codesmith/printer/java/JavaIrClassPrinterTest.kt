package com.github.xyzboom.codesmith.printer.java

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.expressions.IrNew
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.printer.java.JavaIrClassPrinter.Companion.IMPORTS
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JavaIrClassPrinterTest {
    companion object {
        private val todoFunctionBody = "${" ".repeat(8)}throw new RuntimeException();\n"
    }
    @Test
    fun testPrintSimpleClassWithSimpleFunction() {
        val printer = JavaIrClassPrinter()
        val clazzName = "SimpleClassWithSimpleFunction"
        val funcName = "simple"
        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
        val func = IrFunctionDeclaration(funcName, clazz).apply {
            isFinal = true
            body = IrBlock()
        }
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = IMPORTS +
                "public final class $clazzName {\n" +
                "    public final /*@NotNull*/ void $funcName() {\n" +
                todoFunctionBody +
                "    }\n" +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintSimpleClassWithSimpleStubFunction() {
        val printer = JavaIrClassPrinter()
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
        val expect = IMPORTS +
                "public final class $clazzName {\n" +
                "    // stub\n"+
                "    /*\n"+
                "    public final @NotNull void $funcName() {\n" +
                todoFunctionBody +
                "    }\n" +
                "    */\n"+
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintSimpleClassWithFunctionHasParameter() {
        val printer = JavaIrClassPrinter()
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
        val expect = IMPORTS +
                "public final class $clazzName {\n" +
                "    public final /*@NotNull*/ void $funcName(/*@NotNull*/ Object arg0, /*@NotNull*/ $clazzName arg1) {\n" +
                todoFunctionBody +
                "    }\n" +
                "}\n"
        assertEquals(expect, result)
    }

    //<editor-fold desc="Property">
    @Test
    fun testPrintSimpleProperty() {
        val printer = JavaIrClassPrinter()
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
        val expect = IMPORTS +
                "public final class $clazzName {\n" +
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
    fun testPrintNewExpression() {
        val printer = JavaIrClassPrinter()
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
        val expect = IMPORTS +
                "public final class $clazzName {\n" +
                "    public final /*@NotNull*/ void $funcName() {\n" +
                "        new $clazzName();\n" +
                "    }\n" +
                "}\n"
        assertEquals(expect, result)
    }
    //</editor-fold>
}