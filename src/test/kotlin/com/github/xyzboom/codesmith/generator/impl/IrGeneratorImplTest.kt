package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.IrClassType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IrGeneratorImplTest {

    private fun IrFunctionDeclaration.assertIsOverride(
        shouldFrom: List<IrFunctionDeclaration>,
        shouldHasBody: Boolean,
        shouldBeStub: Boolean
    ) {
        assertTrue(isOverride)
        for (func in shouldFrom) {
            assertTrue(signatureEquals(func))
        }
        assertEquals(shouldHasBody, body != null)
        assertEquals(shouldBeStub, isOverrideStub)
    }

    @Test
    fun testGenOverrideFromAbstractSuperAndAnInterface0() {
        val generator = IrGeneratorImpl(
            GeneratorConfig(overrideOnlyMustOnes = true)
        )
        val superName = "Parent"
        val superClass = IrClassDeclaration(superName, IrClassType.ABSTRACT)
        val functionName = "func"
        val function = IrFunctionDeclaration(functionName, superClass)
        superClass.functions.add(function)
        val intfName = "I0"
        val intfClass = IrClassDeclaration(intfName, IrClassType.INTERFACE)
        val functionInIntf = IrFunctionDeclaration(functionName, intfClass)
        intfClass.functions.add(functionInIntf)
        val subClass = IrClassDeclaration("Child", IrClassType.FINAL)
        subClass.superType = superClass.type
        subClass.implementedTypes.add(intfClass.type)
        with(generator) {
            subClass.genOverrides()
        }
        assertEquals(1, subClass.functions.size, "An override function should be generate for subtype!")
        val override = subClass.functions.first()
        override.assertIsOverride(
            listOf(function, functionInIntf),
            shouldHasBody = true, shouldBeStub = false
        )
    }

    @Test
    fun testGenOverrideWhenSuperFunctionsAreConflict() {
        val generator = IrGeneratorImpl(
            GeneratorConfig(overrideOnlyMustOnes = true)
        )
        val superName = "Parent"
        val superClass = IrClassDeclaration(superName, IrClassType.ABSTRACT)
        val functionName = "func"
        val function = IrFunctionDeclaration(functionName, superClass)
        function.body = IrBlock()
        superClass.functions.add(function)
        val intfName = "I0"
        val intfClass = IrClassDeclaration(intfName, IrClassType.INTERFACE)
        val functionInIntf = IrFunctionDeclaration(functionName, intfClass)
        functionInIntf.body = IrBlock()
        intfClass.functions.add(functionInIntf)
        val subClass = IrClassDeclaration("Child", IrClassType.FINAL)
        subClass.superType = superClass.type
        subClass.implementedTypes.add(intfClass.type)
        with(generator) {
            subClass.genOverrides()
        }
        assertEquals(1, subClass.functions.size, "An override function should be generate for subtype!")
        val override = subClass.functions.first()
        override.assertIsOverride(
            listOf(function, functionInIntf),
            shouldHasBody = true, shouldBeStub = false
        )
    }

    @Test
    fun testGenOverrideForAbstractWhenSuperFunctionsAreConflict() {
        val generator = IrGeneratorImpl(
            GeneratorConfig(overrideOnlyMustOnes = true)
        )
        val superName = "Parent"
        val superClass = IrClassDeclaration(superName, IrClassType.ABSTRACT)
        val functionName = "func"
        val function = IrFunctionDeclaration(functionName, superClass)
        function.body = IrBlock()
        superClass.functions.add(function)
        val intfName = "I0"
        val intfClass = IrClassDeclaration(intfName, IrClassType.INTERFACE)
        val functionInIntf = IrFunctionDeclaration(functionName, intfClass)
        functionInIntf.body = IrBlock()
        intfClass.functions.add(functionInIntf)
        val subClass = IrClassDeclaration("Child", IrClassType.ABSTRACT)
        subClass.superType = superClass.type
        subClass.implementedTypes.add(intfClass.type)
        with(generator) {
            subClass.genOverrides()
        }
        assertEquals(1, subClass.functions.size, "An override function should be generate for subtype!")
        val override = subClass.functions.first()
        override.assertIsOverride(
            listOf(function, functionInIntf),
            shouldHasBody = true, shouldBeStub = false
        )
    }

    @Test
    fun testShouldOverrideWhenSuperAbstractShadowDefaultImplInIntf() {
        val generator = IrGeneratorImpl(
            GeneratorConfig(overrideOnlyMustOnes = true)
        )
        val superName = "Parent"
        val superClass = IrClassDeclaration(superName, IrClassType.ABSTRACT)
        val functionName = "func"
        val function = IrFunctionDeclaration(functionName, superClass)
        superClass.functions.add(function)
        val intfName = "I0"
        val intfClass = IrClassDeclaration(intfName, IrClassType.INTERFACE)
        val functionInIntf = IrFunctionDeclaration(functionName, intfClass)
        functionInIntf.body = IrBlock()
        intfClass.functions.add(functionInIntf)
        val subClass = IrClassDeclaration("Child", IrClassType.FINAL)
        subClass.superType = superClass.type
        subClass.implementedTypes.add(intfClass.type)
        with(generator) {
            subClass.genOverrides()
        }
        assertEquals(1, subClass.functions.size, "An override function should be generate for subtype!")
        val override = subClass.functions.first()
        override.assertIsOverride(
            listOf(function, functionInIntf),
            shouldHasBody = true, shouldBeStub = false
        )
    }
}