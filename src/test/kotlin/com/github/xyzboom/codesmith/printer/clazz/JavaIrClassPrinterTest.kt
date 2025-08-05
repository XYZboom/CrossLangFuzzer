package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.printer.clazz.JavaIrClassPrinter.Companion.NULLABILITY_ANNOTATION_IMPORTS
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
        val clazz = buildClassDeclaration {
            name = clazzName
            classKind = ClassKind.FINAL
        }
        val func = buildFunctionDeclaration {
            name = funcName
            isFinal = true
            containingClassName = clazzName
            body = buildBlock()
            parameterList = buildParameterList()
        }
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = NULLABILITY_ANNOTATION_IMPORTS +
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
        val clazz = buildClassDeclaration {
            name = clazzName
            classKind = ClassKind.FINAL
        }
        val func = buildFunctionDeclaration {
            name = funcName
            isFinal = true
            containingClassName = clazzName
            body = buildBlock()
            isOverride = true
            isOverrideStub = true
            parameterList = buildParameterList()
        }
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = NULLABILITY_ANNOTATION_IMPORTS +
                "public final class $clazzName {\n" +
                "    // stub\n"+
                "    /*\n"+
                "    @Override\n"+
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
        val clazz = buildClassDeclaration {
            name = clazzName
            classKind = ClassKind.FINAL
        }
        val func = buildFunctionDeclaration {
            name = funcName
            isFinal = true
            containingClassName = clazzName
            body = buildBlock()
            parameterList = buildParameterList {
                parameters.add(buildParameter {
                    name = "arg0"
                    type = IrAny
                })
                parameters.add(buildParameter {
                    name = "arg1"
                    type = clazz.type
                })
            }
        }
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = NULLABILITY_ANNOTATION_IMPORTS +
                "public final class $clazzName {\n" +
                "    public final /*@NotNull*/ void $funcName(/*@NotNull*/ Object arg0, /*@NotNull*/ $clazzName arg1) {\n" +
                todoFunctionBody +
                "    }\n" +
                "}\n"
        assertEquals(expect, result)
    }
//
//    //<editor-fold desc="Property">
//    @Test
//    fun testPrintSimpleProperty() {
//        val printer = JavaIrClassPrinter()
//        val clazzName = "SimpleClassWithSimpleFunction"
//        val propertyTypeName = "PType"
//        val propertyName = "simple"
//        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
//        val pClass = IrClassDeclaration(propertyTypeName, IrClassType.FINAL)
//        val property = IrPropertyDeclaration(propertyName, clazz).apply {
//            isFinal = true
//            type = pClass.type
//            readonly = true
//        }
//        clazz.properties.add(property)
//        val result = printer.print(clazz)
//        val expect = NULLABILITY_ANNOTATION_IMPORTS +
//                "public final class $clazzName {\n" +
//                "    public final /*@NotNull*/ $propertyTypeName " +
//                "get${propertyName.replaceFirstChar { it.uppercaseChar() }}() {\n" +
//                todoFunctionBody +
//                "    }\n" +
//                "}\n"
//        assertEquals(expect, result)
//    }
//    //</editor-fold>
//
//    //<editor-fold desc="Expression">
//    @Test
//    fun testPrintNewExpression() {
//        val printer = JavaIrClassPrinter()
//        val clazzName = "SimpleClassWithSimpleFunction"
//        val funcName = "simple"
//        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
//        val func = IrFunctionDeclaration(funcName, clazz).apply {
//            isFinal = true
//            body = IrBlock().apply {
//                expressions.add(IrNew.create(clazz.type))
//            }
//        }
//        clazz.functions.add(func)
//        val result = printer.print(clazz)
//        val expect = NULLABILITY_ANNOTATION_IMPORTS +
//                "public final class $clazzName {\n" +
//                "    public final /*@NotNull*/ void $funcName() {\n" +
//                "        new $clazzName();\n" +
//                "    }\n" +
//                "}\n"
//        assertEquals(expect, result)
//    }
//    //</editor-fold>
}