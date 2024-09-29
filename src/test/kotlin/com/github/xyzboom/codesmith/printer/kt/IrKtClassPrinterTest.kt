package com.github.xyzboom.codesmith.printer.kt

import com.github.xyzboom.codesmith.ir.declarations.builtin.AnyClass
import com.github.xyzboom.codesmith.ir.declarations.impl.IrClassImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrFileImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrModuleImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrPackageImpl
import com.github.xyzboom.codesmith.ir.types.impl.IrTypeParameterImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IrKtClassPrinterTest {

    private val printer = IrKtClassPrinter()
    private val mockPackage = IrPackageImpl("mockedpkg", null, IrModuleImpl.builtin)
    private val mockFile = IrFileImpl("mockedfile", mockPackage)

    @Test
    fun testPrintIrConcreteType() {
        val myClassName = "MyClass"
        val myClass = IrClassImpl(myClassName, mockFile)
        assertEquals("MyClass", printer.printIrConcreteType(myClass.type))
        val classWithTypeParamName = "ClassWithTypeParam"
        val classWithTypeParam = IrClassImpl(
            classWithTypeParamName, mockFile,
            typeParameters = mutableListOf(IrTypeParameterImpl(AnyClass.type, "T"))
        )
        assertEquals("ClassWithTypeParam<T>", printer.printIrConcreteType(classWithTypeParam.type))
    }
}