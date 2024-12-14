package com.github.xyzboom.codesmith.printerOld.java

import com.github.xyzboom.codesmith.irOld.declarations.builtin.AnyClass
import com.github.xyzboom.codesmith.irOld.declarations.impl.*
import com.github.xyzboom.codesmith.irOld.expressions.impl.IrConstructorCallExpressionImpl
import com.github.xyzboom.codesmith.irOld.expressions.impl.IrFunctionCallExpressionImpl
import com.github.xyzboom.codesmith.irOld.types.IrFileType
import com.github.xyzboom.codesmith.irOld.types.builtin.IrBuiltinTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IrJavaClassPrinterTest {
    private val printer = IrJavaClassPrinter()
    private val mockPackage = IrPackageImpl("mockedpkg", null, IrModuleImpl.builtin)
    private val mockFile = IrFileImpl("mockedfile", mockPackage, IrFileType.JAVA)

    @Test
    fun testPrintFunctionWithParamInClass() {
        val myClassName = "MyClass"
        val myClass = IrClassImpl(myClassName, mockFile, superType = IrBuiltinTypes.ANY)
        myClass.functions.add(
            IrFunctionImpl(
                "func", myClass, returnType = IrBuiltinTypes.ANY
            ).apply {
                valueParameters.add(IrValueParameterImpl("p1", IrBuiltinTypes.BOOLEAN))
                expressions.add(IrConstructorCallExpressionImpl(AnyClass.constructor, emptyList()))
            })
        // note that Java public class must be contained in a Java file with same name
        val expected = "// FILE: MyClass.java\n" +
                "package mockedpkg;\n" +
                "public final class MyClass extends Object {\n" +
                "\tpublic Object func(Boolean p1) {\n" +
                "\t\treturn new Object();\n" +
                "\t}\n" +
                "\tpublic MyClass() {\n" +
                "\t\tsuper();\n" +
                "\t}\n" +
                "}\n"
        assertEquals(expected, printer.print(myClass))
    }

    @Test
    fun testPrintFunctionWithExprInClass() {
        val otherClass = IrClassImpl("Other", mockFile, superType = IrBuiltinTypes.ANY)
        otherClass.functions.add(IrFunctionImpl(
            "func", otherClass, returnType = IrBuiltinTypes.BOOLEAN
        ))
        val myClassName = "MyClass"
        val myClass = IrClassImpl(myClassName, mockFile, superType = IrBuiltinTypes.ANY)
        myClass.functions.add(IrFunctionImpl(
            "func", myClass, returnType = IrBuiltinTypes.ANY
        ).apply {
            val newOther = IrConstructorCallExpressionImpl(otherClass.specialConstructor!!, emptyList())
            expressions.add(IrFunctionCallExpressionImpl(newOther, otherClass.functions[0], emptyList()))
            expressions.add(IrConstructorCallExpressionImpl(AnyClass.constructor, emptyList()))
        })
        val expected = "// FILE: MyClass.java\n" +
                "package mockedpkg;\n" +
                "public final class MyClass extends Object {\n" +
                "\tpublic Object func() {\n" +
                "\t\tnew mockedpkg.Other().func();\n" +
                "\t\treturn new Object();\n" +
                "\t}\n" +
                "\tpublic MyClass() {\n" +
                "\t\tsuper();\n" +
                "\t}\n" +
                "}\n"
        assertEquals(expected, printer.print(myClass))
    }
}