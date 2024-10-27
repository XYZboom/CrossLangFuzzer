package com.github.xyzboom.codesmith.printer.kt

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.declarations.builtin.AnyClass
import com.github.xyzboom.codesmith.ir.declarations.impl.*
import com.github.xyzboom.codesmith.ir.expressions.impl.IrConstructorCallExpressionImpl
import com.github.xyzboom.codesmith.ir.expressions.impl.IrFunctionCallExpressionImpl
import com.github.xyzboom.codesmith.ir.types.IrFileType
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltinTypes
import com.github.xyzboom.codesmith.ir.types.impl.IrTypeParameterImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IrKtClassPrinterTest {

    private val printer = IrKtClassPrinter()
    private val mockPackage = IrPackageImpl("mockedpkg", null, IrModuleImpl.builtin)
    private val mockFile = IrFileImpl("mockedfile", mockPackage, IrFileType.KOTLIN)

    @Test
    fun testPrintIrConcreteType() {
        val myClassName = "MyClass"
        val myClass = IrClassImpl(myClassName, mockFile)
        assertEquals("mockedpkg.MyClass", printer.printIrConcreteType(myClass.type))
        val classWithTypeParamName = "ClassWithTypeParam"
        val classWithTypeParam = IrClassImpl(
            classWithTypeParamName, mockFile,
            typeParameters = mutableListOf(IrTypeParameterImpl(AnyClass.type, "T"))
        )
        assertEquals("mockedpkg.ClassWithTypeParam<T>", printer.printIrConcreteType(classWithTypeParam.type))
    }

    @Test
    fun testPrintClassWithNoDecl() {
        val myClassName = "MyClass"
        val myClass = IrClassImpl(myClassName, mockFile)
        val expected = "public class MyClass  {\n" +
                "\tpublic constructor(): super() {\n" +
                "\t}\n" +
                "}\n"
        assertEquals(expected, printer.print(myClass))
    }

    @Test
    fun testPrintClassWithDifferentNoArgConstructor() {
        val myClassName = "MyClass"
        val myClass = IrClassImpl(myClassName, mockFile, superType = IrBuiltinTypes.ANY)
        myClass.functions.add(IrConstructorImpl(
            IrAccessModifier.PRIVATE, myClass,
            IrConstructorCallExpressionImpl(AnyClass.constructor, emptyList())
        ))
        val privateExpected = "public class MyClass : Any {\n" +
                "\tprivate constructor(): super() {\n" +
                "\t}\n" +
                "}\n"
        assertEquals(privateExpected, printer.print(myClass))
        myClass.functions.clear()
        myClass.functions.add(IrConstructorImpl(
            IrAccessModifier.PROTECTED, myClass,
            IrConstructorCallExpressionImpl(AnyClass.constructor, emptyList())
        ))
        val protectedExpected = "public class MyClass : Any {\n" +
                "\tprotected constructor(): super() {\n" +
                "\t}\n" +
                "}\n"
        assertEquals(protectedExpected, printer.print(myClass))
        val myChildClassName = "MyChildClass"
        val myChildClass = IrClassImpl(myChildClassName, mockFile, superType = myClass.type)
        val myChildExpected = "public class MyChildClass : mockedpkg.MyClass {\n" +
                "\tpublic constructor(): super() {\n" +
                "\t}\n" +
                "}\n"
        assertEquals(myChildExpected, printer.print(myChildClass))
    }

    @Test
    fun testPrintFunctionInClass() {
        val myClassName = "MyClass"
        val myClass = IrClassImpl(myClassName, mockFile, superType = IrBuiltinTypes.ANY)
        myClass.functions.add(IrFunctionImpl(
            "func", myClass, returnType = IrBuiltinTypes.ANY
        ).apply {
            expressions.add(IrConstructorCallExpressionImpl(AnyClass.constructor, emptyList()))
        })
        val expected = "public class MyClass : Any {\n" +
                "\tpublic fun func(): Any {\n" +
                "\t\treturn Any()\n" +
                "\t}\n" +
                "\tpublic constructor(): super() {\n" +
                "\t}\n" +
                "}\n"
        assertEquals(expected, printer.print(myClass))
    }

    @Test
    fun testPrintFunctionWithParamInClass() {
        val myClassName = "MyClass"
        val myClass = IrClassImpl(myClassName, mockFile, superType = IrBuiltinTypes.ANY)
        myClass.functions.add(IrFunctionImpl(
            "func", myClass, returnType = IrBuiltinTypes.ANY
        ).apply {
            valueParameters.add(IrValueParameterImpl("p1", IrBuiltinTypes.BOOLEAN))
            expressions.add(IrConstructorCallExpressionImpl(AnyClass.constructor, emptyList()))
        })
        val expected = "public class MyClass : Any {\n" +
                "\tpublic fun func(p1: Boolean): Any {\n" +
                "\t\treturn Any()\n" +
                "\t}\n" +
                "\tpublic constructor(): super() {\n" +
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
        val expected = "public class MyClass : Any {\n" +
                "\tpublic fun func(): Any {\n" +
                "\t\tmockedpkg.Other().func()\n" +
                "\t\treturn Any()\n" +
                "\t}\n" +
                "\tpublic constructor(): super() {\n" +
                "\t}\n" +
                "}\n"
        assertEquals(expected, printer.print(myClass))
    }
}