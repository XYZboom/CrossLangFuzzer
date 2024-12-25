package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.assertIsOverride
import com.github.xyzboom.codesmith.assertParameters
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IrDeclGeneratorImplTest {
    //<editor-fold desc="Override">
    //<editor-fold desc="Normal">

    @Test
    fun testGenOverrideFromAbstractSuperAndAnInterface0() {
        val generator = IrDeclGeneratorImpl(
            GeneratorConfig.testDefault
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
            true, shouldHasBody = true, shouldBeStub = false
        )
    }

    @Test
    fun testGenOverrideWhenSuperFunctionsAreConflict() {
        val generator = IrDeclGeneratorImpl(
            GeneratorConfig.testDefault
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
            true, shouldHasBody = true, shouldBeStub = false
        )
    }

    @Test
    fun testGenOverrideForAbstractWhenSuperFunctionsAreConflict() {
        val generator = IrDeclGeneratorImpl(
            GeneratorConfig.testDefault
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
            true, shouldHasBody = true, shouldBeStub = false
        )
    }

    @Test
    fun testShouldOverrideWhenSuperAbstractShadowDefaultImplInIntf() {
        val generator = IrDeclGeneratorImpl(
            GeneratorConfig.testDefault
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
            true, shouldHasBody = true, shouldBeStub = false
        )
    }

    @Test
    fun testShouldOverrideWhenSuperSuperShadowDefaultImplInIntf() {
        val generator = IrDeclGeneratorImpl(
            GeneratorConfig.testDefault
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
            true,
            shouldHasBody = true,
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
            true,
            shouldHasBody = true,
            shouldBeStub = false
        )
    }

    @Test
    fun testGenOverrideComplex() {
        val generator = IrDeclGeneratorImpl(
            GeneratorConfig.testDefault
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
            true,
            shouldHasBody = true,
            shouldBeStub = true
        )

        with(generator) {
            openC.genOverrides()
        }
        assertEquals(
            1, openC.functions.size,
            "An override function should be generate for openC when overrideOnlyMustOnes is true"
        )
        openC.functions.single().assertIsOverride(
            listOf(funcInAbsP, funcInI1),
            true,
            shouldHasBody = true,
            shouldBeStub = false
        )
    }

    @Test
    fun testStubForFinalIsStillFinal() {
        val generator = IrDeclGeneratorImpl(
            GeneratorConfig.testDefault
        )
        val superClass = IrClassDeclaration("P", IrClassType.OPEN)
        val childClass = IrClassDeclaration("C", IrClassType.FINAL)
        childClass.superType = superClass.type

        val funcInSuper = IrFunctionDeclaration("func", superClass)
        funcInSuper.body = IrBlock()
        funcInSuper.isFinal = true
        superClass.functions.add(funcInSuper)

        with(generator) {
            childClass.genOverrides()
        }

        assertEquals(1, childClass.functions.size)
        val function = childClass.functions.single()
        function.assertIsOverride(
            listOf(funcInSuper),
            true,
            shouldHasBody = true,
            shouldBeStub = true,
            shouldBeFinal = true
        )
    }

    @Test
    fun testStubForFinalStubIsStillFinal() {
        val generator = IrDeclGeneratorImpl(
            GeneratorConfig.testDefault
        )
        val superClass = IrClassDeclaration("P", IrClassType.OPEN)
        val childClass = IrClassDeclaration("C", IrClassType.OPEN)
        childClass.superType = superClass.type

        val funcInSuper = IrFunctionDeclaration("func", superClass)
        funcInSuper.body = null
        funcInSuper.isOverrideStub = true
        funcInSuper.isOverride = true
        funcInSuper.isFinal = true
        superClass.functions.add(funcInSuper)

        with(generator) {
            childClass.genOverrides()
        }

        assertEquals(1, childClass.functions.size)
        val function = childClass.functions.single()
        function.assertIsOverride(
            listOf(funcInSuper),
            true,
            shouldHasBody = true,
            shouldBeStub = true,
            shouldBeFinal = true
        )
    }

    @Test
    fun testChildAbstractInIntfShouldShadowParentIntf() {
        val generator = IrDeclGeneratorImpl(GeneratorConfig.testDefault)

        val i0 = IrClassDeclaration("I0", IrClassType.INTERFACE)
        val i1 = IrClassDeclaration("I1", IrClassType.INTERFACE)
        i1.implementedTypes.add(i0.type)

        val funcInI0 = IrFunctionDeclaration("func", i0)
        funcInI0.body = IrBlock()
        val funcInI1 = IrFunctionDeclaration("func", i1)
        funcInI1.override.add(funcInI0)
        funcInI1.isOverride = true

        i0.functions.add(funcInI0)
        i1.functions.add(funcInI1)

        val clazz = IrClassDeclaration("C", IrClassType.FINAL)
        clazz.implementedTypes.add(i0.type)
        clazz.implementedTypes.add(i1.type)

        with(generator) {
            clazz.genOverrides()
        }

        assertEquals(1, clazz.functions.size)
        clazz.functions.single().assertIsOverride(
            listOf(funcInI1),
            true,
            shouldHasBody = true,
            shouldBeStub = false,
            shouldBeFinal = false
        )
    }

    @Test
    fun testMustOverrideWhenSuperStubConflictWithIntf() {
        val generator = IrDeclGeneratorImpl(GeneratorConfig.testDefault)

        /**
         * I0&
         * I1: I0#
         * I2: I0#
         * P: I2^
         * C: P, I1
         * conflict in C from I1 and I2
         * & means abstract function
         * # means implement function
         * ^ means stub function
         */
        val i0 = IrClassDeclaration("I0", IrClassType.INTERFACE)
        val i1 = IrClassDeclaration("I1", IrClassType.INTERFACE)
        val i2 = IrClassDeclaration("I2", IrClassType.INTERFACE)
        val p = IrClassDeclaration("P", IrClassType.OPEN)
        val c = IrClassDeclaration("C", IrClassType.FINAL)
        i1.implementedTypes.add(i0.type)
        i2.implementedTypes.add(i0.type)
        p.implementedTypes.add(i2.type)
        c.superType = p.type
        c.implementedTypes.add(i1.type)

        val funcInI0 = IrFunctionDeclaration("func", i0).apply {
            i0.functions.add(this)
        }
        val funcInI1 = IrFunctionDeclaration("func", i1).apply {
            body = IrBlock()
            isOverride = true
            override.add(funcInI0)
            i1.functions.add(this)
        }
        val funcInI2 = IrFunctionDeclaration("func", i2).apply {
            body = IrBlock()
            isOverride = true
            override.add(funcInI0)
            i2.functions.add(this)
        }
        val funcInP = IrFunctionDeclaration("func", p).apply {
            body = IrBlock()
            isOverride = true
            isOverrideStub = true
            override.add(funcInI2)
            p.functions.add(this)
        }
        with(generator) {
            c.genOverrides()
        }
        c.functions.single().assertIsOverride(
            listOf(funcInP, funcInI1),
            true,
            shouldHasBody = true,
            shouldBeStub = false,
            shouldBeFinal = false
        )
    }

    @Test
    fun testMustOverrideWhenOverrideOfSuperStubWasOverrideByIntf() {
        val generator = IrDeclGeneratorImpl(GeneratorConfig.testDefault)

        /**
         * I0#
         * I1: I0&
         * P: I0^
         * C: P, I1
         *
         * & means abstract function
         * # means implement function
         * ^ means stub function
         */
        val i0 = IrClassDeclaration("I0", IrClassType.INTERFACE)
        val i1 = IrClassDeclaration("I1", IrClassType.INTERFACE)
        i1.implementedTypes.add(i0.type)
        val p = IrClassDeclaration("P", IrClassType.OPEN)
        p.implementedTypes.add(i0.type)
        val c = IrClassDeclaration("C", IrClassType.FINAL)
        c.superType = p.type
        c.implementedTypes.add(i1.type)

        val funcInI0 = IrFunctionDeclaration("func", i0).apply {
            body = IrBlock()
            i0.functions.add(this)
        }
        val funcInI1 = IrFunctionDeclaration("func", i1).apply {
            body = null
            isOverride = true
            override.add(funcInI0)
            i1.functions.add(this)
        }
        val funcInP = IrFunctionDeclaration("func", p).apply {
            body = IrBlock()
            isOverride = true
            override.add(funcInI0)
            isOverrideStub = true
            p.functions.add(this)
        }
        with(generator) {
            c.genOverrides()
        }
        c.functions.single().assertIsOverride(
            listOf(funcInP, funcInI1),
            true,
            shouldHasBody = true,
            shouldBeStub = false,
            shouldBeFinal = false
        )
    }

    @Test
    fun testMustOverrideWhenConflictInIntf() {
        /**
         * I0&
         * I1: I0&
         * I2: I0#
         * I3: I1, I2
         *
         * & means abstract function
         * # means implement function
         * ^ means stub function
         */
        val generator = IrDeclGeneratorImpl(GeneratorConfig.testDefault)
        val i0 = IrClassDeclaration("I0", IrClassType.INTERFACE)
        val i1 = IrClassDeclaration("I1", IrClassType.INTERFACE)
        i1.implementedTypes.add(i0.type)
        val i2 = IrClassDeclaration("I2", IrClassType.INTERFACE)
        i2.implementedTypes.add(i0.type)
        val i3 = IrClassDeclaration("I3", IrClassType.INTERFACE)
        i3.implementedTypes.add(i1.type)
        i3.implementedTypes.add(i2.type)

        val funcInI0 = IrFunctionDeclaration("func", i0)
        i0.functions.add(funcInI0)
        val funcInI1 = IrFunctionDeclaration("func", i1).apply {
            isOverride = true
            override.add(funcInI0)
            i1.functions.add(this)
        }
        val funcInI2 = IrFunctionDeclaration("func", i2).apply {
            isOverride = true
            body = IrBlock()
            override.add(funcInI0)
            i2.functions.add(this)
        }
        with(generator) {
            i3.genOverrides()
        }
        i3.functions.single().assertIsOverride(
            listOf(funcInI1, funcInI2),
            true,
            shouldHasBody = true,
            shouldBeStub = false,
            shouldBeFinal = false
        )
    }

    @Test
    fun testMustOverrideWhenConflictInIntf2() {
        /**
         * I0#
         * I1&
         * P: I0^
         * C: P, I1
         *
         * & means abstract function
         * # means implement function
         * ^ means stub function
         */
        val generator = IrDeclGeneratorImpl(GeneratorConfig.testDefault)
        val i0 = IrClassDeclaration("I0", IrClassType.INTERFACE)
        val i1 = IrClassDeclaration("I1", IrClassType.INTERFACE)
        val p = IrClassDeclaration("P", IrClassType.OPEN)
        p.implementedTypes.add(i0.type)
        val c = IrClassDeclaration("C", IrClassType.FINAL)
        c.superType = p.type
        c.implementedTypes.add(i1.type)

        val funcInI0 = IrFunctionDeclaration("func", i0).apply {
            body = IrBlock()
            i0.functions.add(this)
        }
        val funcInI1 = IrFunctionDeclaration("func", i1).apply {
            i1.functions.add(this)
        }
        val funcInP = IrFunctionDeclaration("func", p).apply {
            body = IrBlock()
            isOverride = true
            isOverrideStub = true
            override.add(funcInI0)
            p.functions.add(this)
        }
        with(generator) {
            c.genOverrides()
        }
        c.functions.single().assertIsOverride(
            listOf(funcInI1, funcInP),
            true,
            shouldHasBody = true,
            shouldBeStub = false,
            shouldBeFinal = false
        )
    }

    @Test
    fun testOverrideStubIsNotMustOverride() {
        val generator = IrDeclGeneratorImpl(GeneratorConfig.testDefault)
        val gp = IrClassDeclaration("GP", IrClassType.OPEN)
        val p = IrClassDeclaration("P", IrClassType.OPEN).apply {
            superType = gp.type
        }
        val c = IrClassDeclaration("C", IrClassType.OPEN).apply {
            superType = p.type
        }
        val funcInGp = IrFunctionDeclaration("func", gp).apply {
            body = IrBlock()
            gp.functions.add(this)
        }
        with(generator) {
            p.genOverrides()
        }
        val funcInP = p.functions.single()
        funcInP.assertIsOverride(
            listOf(funcInGp),
            shouldBeSameSignature = false,
            shouldHasBody = true,
            shouldBeStub = true,
            shouldBeFinal = false
        )

        with(generator) {
            c.genOverrides()
        }
        val funcInC = c.functions.single()
        funcInC.assertIsOverride(
            listOf(funcInP),
            shouldBeSameSignature = false,
            shouldHasBody = true,
            shouldBeStub = true,
            shouldBeFinal = false
        )
    }
    //</editor-fold>


    //<editor-fold desc="Generic">
    @Test
    fun testOverrideWithCorrectParameterWhenSuperHasGeneric() {
        /**
         * GP<T0>#
         * P<T1>: GP<T1>#
         * C<T2>: P<T2>#
         * # means implement function
         */
        val generator = IrDeclGeneratorImpl(GeneratorConfig.testDefault)
        val t0 = IrTypeParameter.create("T0", IrAny)
        val t1 = IrTypeParameter.create("T1", IrAny)
        val t2 = IrTypeParameter.create("T2", IrAny)
        val gp = IrClassDeclaration("GP", IrClassType.OPEN).apply {
            typeParameters.add(t0)
        }
        val p = IrClassDeclaration("P", IrClassType.OPEN).apply {
            typeParameters.add(t1)
            val rawGp = gp.type as IrParameterizedClassifier
            rawGp.putTypeArgument(t0, t1)
            superType = rawGp
        }
        val c = IrClassDeclaration("C", IrClassType.OPEN).apply {
            typeParameters.add(t2)
            val rawP = p.type as IrParameterizedClassifier
            rawP.putTypeArgument(t1, t2)
            superType = rawP
        }
        val funcInGp = IrFunctionDeclaration("func", gp).apply {
            body = IrBlock()
            gp.functions.add(this)
            parameterList.parameters.add(IrParameter("arg", t0))
        }
        with(generator) {
            p.genOverrides()
        }
        val funcInP = p.functions.single()
        funcInP.assertIsOverride(
            listOf(funcInGp),
            shouldBeSameSignature = false,
            shouldHasBody = true,
            shouldBeStub = true,
            shouldBeFinal = false
        )
        funcInP.assertParameters(
            listOf(
                "arg" to t1
            )
        )

        with(generator) {
            c.genOverrides()
        }
        val funcInC = c.functions.single()
        funcInC.assertIsOverride(
            listOf(funcInP),
            shouldBeSameSignature = false,
            shouldHasBody = true,
            shouldBeStub = true,
            shouldBeFinal = false
        )
        funcInC.assertParameters(
            listOf(
                "arg" to t2
            )
        )
    }
    //</editor-fold>
    //</editor-fold>
}