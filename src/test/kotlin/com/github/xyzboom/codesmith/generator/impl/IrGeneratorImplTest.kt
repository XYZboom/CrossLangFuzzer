package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.IrClassType
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
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
        assertEquals(shouldFrom.size, override.size)
        assertContentEquals(shouldFrom.sortedBy { it.hashCode() }, override.sortedBy { it.hashCode() })
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

    @Test
    fun testShouldOverrideWhenSuperSuperShadowDefaultImplInIntf() {
        val generator = IrGeneratorImpl(
            GeneratorConfig(overrideOnlyMustOnes = true)
        )
        val superName = "GrandParent"
        val superClass = IrClassDeclaration(superName, IrClassType.ABSTRACT)
        val functionName = "func"
        val function = IrFunctionDeclaration(functionName, superClass)
        function.body = IrBlock()
        superClass.functions.add(function)

        val subClass = IrClassDeclaration("Parent", IrClassType.FINAL)
        subClass.superType = superClass.type
        with(generator) {
            subClass.genOverrides()
        }
        assertEquals(
            1, subClass.functions.size,
            "An stub override function should be generate for subtype when overrideOnlyMustOnes is true"
        )
        val funcInSub = subClass.functions.single()
        funcInSub.assertIsOverride(
            listOf(function),
            shouldHasBody = false,
            shouldBeStub = true
        )

        val intfName = "I0"
        val intfClass = IrClassDeclaration(intfName, IrClassType.INTERFACE)
        val functionInIntf = IrFunctionDeclaration(functionName, intfClass)
        functionInIntf.body = IrBlock()
        intfClass.functions.add(functionInIntf)

        val subSubName = "Child"
        val subSubClass = IrClassDeclaration(subSubName, IrClassType.ABSTRACT)
        subSubClass.superType = subClass.type
        subSubClass.implementedTypes.add(intfClass.type)
        with(generator) {
            subSubClass.genOverrides()
        }
        assertEquals(
            1, subSubClass.functions.size,
            "An override function should be generate for subSubtype!"
        )
        subSubClass.functions.single().assertIsOverride(
            listOf(functionInIntf, funcInSub),
            shouldHasBody = true,
            shouldBeStub = false
        )
    }

    @Test
    fun testGenOverrideComplex() {
        val generator = IrGeneratorImpl(
            GeneratorConfig(overrideOnlyMustOnes = true)
        )

        /**
         *         I0&
         *       |    \
         *  GrandP#    I1#
         *      |      |
         *     AbsP    |
         *        \  /
         *        OpenC
         * & means abstract function
         * # means implement function
         */
        val i0 = IrClassDeclaration("I0", IrClassType.INTERFACE)
        val i1 = IrClassDeclaration("I1", IrClassType.INTERFACE)
        i1.implementedTypes.add(i0.type)
        val grandP = IrClassDeclaration("GrandP", IrClassType.OPEN)
        grandP.implementedTypes.add(i0.type)
        val absP = IrClassDeclaration("AbsP", IrClassType.ABSTRACT)
        absP.superType = grandP.type
        val openC = IrClassDeclaration("OpenC", IrClassType.OPEN)
        openC.superType = absP.type
        openC.implementedTypes.add(i1.type)

        val funcName = "func"
        val funcInI0 = IrFunctionDeclaration(funcName, i0)
        i0.functions.add(funcInI0)
        val funcInI1 = IrFunctionDeclaration(funcName, i1)
        funcInI1.isOverride = true
        funcInI1.body = IrBlock()
        funcInI1.override.add(funcInI0)
        i1.functions.add(funcInI1)
        val funcInGrandP = IrFunctionDeclaration(funcName, grandP)
        funcInGrandP.isOverride = true
        funcInGrandP.body = IrBlock()
        funcInGrandP.override.add(funcInI0)
        grandP.functions.add(funcInGrandP)
        with(generator) {
            absP.genOverrides()
        }
        assertEquals(
            1, absP.functions.size,
            "An stub override function should be generate for absP when overrideOnlyMustOnes is true"
        )
        val funcInAbsP = absP.functions.single()
        funcInAbsP.assertIsOverride(
            listOf(funcInGrandP),
            shouldHasBody = false,
            shouldBeStub = true
        )

        with(generator) {
            openC.genOverrides()
        }
        assertEquals(
            1, openC.functions.size,
            "An stub override function should be generate for absP when overrideOnlyMustOnes is true"
        )
        openC.functions.single().assertIsOverride(
            listOf(funcInAbsP, funcInI1),
            shouldHasBody = true,
            shouldBeStub = false
        )
    }
}