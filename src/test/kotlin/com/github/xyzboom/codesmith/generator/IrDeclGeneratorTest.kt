package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.assertIsOverride
import com.github.xyzboom.codesmith.assertParameters
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.type
import io.kotest.matchers.maps.shouldMatchExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IrDeclGeneratorTest {
    val mockProgram
        get() = buildProgram()

    //<editor-fold desc="Gen super">
    @Test
    fun testSuperArgumentsIsCorrect() {
        /**
         * P<T0>
         * C<T1>: P<T1>
         */
        val prog = mockProgram
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
        val p = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.OPEN
            typeParameters.add(t0)
        }
        prog.classes.add(p)
        val c = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.FINAL
            typeParameters.add(t1)
        }
        prog.classes.add(c)

        val generator = SequentialTypeSelectionIrDeclGenerator(
            listOf(p.type, t1)
        )
        with(generator) {
            c.genSuperTypes(prog)
        }
        c.allSuperTypeArguments.shouldMatchExactly(
            IrTypeParameterName(t0.name) to {
                it.first shouldBe IrTypeMatcher(t0)
                it.second shouldBe IrTypeMatcher(t1)
            }
        )
    }

    @Test
    fun testSuperArgumentsIsCorrect2() {
        /**
         * P<T0>
         * C<T1>: P<T1>
         * GC<T2>: C<T2>
         */
        val prog = mockProgram
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
        val t2 = buildTypeParameter { name = "T2"; upperbound = IrAny }
        val p = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.OPEN
            typeParameters.add(t0)
        }
        prog.classes.add(p)
        val c = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.OPEN
            typeParameters.add(t1)
            superType = p.type.apply {
                this as IrParameterizedClassifier
                putTypeArgument(t0, t1)
            }
            allSuperTypeArguments[IrTypeParameterName(t0.name)] = t0 to t1
        }
        prog.classes.add(c)
        val gc = buildClassDeclaration {
            name = "GC"
            classKind = ClassKind.FINAL
            typeParameters.add(t2)
        }
        prog.classes.add(gc)
        val generator = SequentialTypeSelectionIrDeclGenerator(
            listOf(c.type, t2)
        )
        with(generator) {
            gc.genSuperTypes(prog)
        }
        gc.allSuperTypeArguments.shouldMatchExactly(
            IrTypeParameterName(t0.name) to {
                it.first shouldBe IrTypeMatcher(t0)
                it.second shouldBe IrTypeMatcher(t2)
            },
            IrTypeParameterName(t1.name) to {
                it.first shouldBe IrTypeMatcher(t1)
                it.second shouldBe IrTypeMatcher(t2)
            },
        )
    }
    //</editor-fold>

    //<editor-fold desc="Override">
    //<editor-fold desc="Normal">

    @Test
    fun testGenOverrideFromAbstractSuperAndAnInterface0() {
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val superName = "Parent"
        val superClass = buildClassDeclaration {
            name = superName
            classKind = ClassKind.ABSTRACT
        }
        val functionName = "func"
        val function = buildFunctionDeclaration {
            name = functionName
            containingClassName = superName
            parameterList = buildParameterList()
        }
        superClass.functions.add(function)
        val intfName = "I0"
        val intfClass = buildClassDeclaration {
            name = intfName
            classKind = ClassKind.INTERFACE
        }
        val functionInIntf = buildFunctionDeclaration {
            name = functionName
            containingClassName = intfName
            parameterList = buildParameterList()
        }
        intfClass.functions.add(functionInIntf)
        val subClass = buildClassDeclaration {
            name = "Child"
            classKind = ClassKind.FINAL
        }
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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val superName = "Parent"
        val superClass = buildClassDeclaration {
            name = superName
            classKind = ClassKind.ABSTRACT
        }
        val functionName = "func"
        val function = buildFunctionDeclaration {
            name = functionName
            containingClassName = superName
            parameterList = buildParameterList()
        }
        function.body = buildBlock()
        superClass.functions.add(function)
        val intfName = "I0"
        val intfClass = buildClassDeclaration {
            name = intfName
            classKind = ClassKind.INTERFACE
        }
        val functionInIntf = buildFunctionDeclaration {
            name = functionName
            containingClassName = intfName
            parameterList = buildParameterList()
        }
        functionInIntf.body = buildBlock()
        intfClass.functions.add(functionInIntf)
        val subClass = buildClassDeclaration {
            name = "Child"
            classKind = ClassKind.FINAL
        }
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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val superName = "Parent"
        val superClass = buildClassDeclaration {
            name = superName
            classKind = ClassKind.ABSTRACT
        }
        val functionName = "func"
        val function = buildFunctionDeclaration {
            name = functionName
            containingClassName = superName
            body = buildBlock()
            parameterList = buildParameterList()
        }
        superClass.functions.add(function)
        val intfName = "I0"
        val intfClass = buildClassDeclaration {
            name = superName
            classKind = ClassKind.INTERFACE
        }
        val functionInIntf = buildFunctionDeclaration {
            name = functionName
            containingClassName = intfName
            body = buildBlock()
            parameterList = buildParameterList()
        }
        intfClass.functions.add(functionInIntf)
        val subClass = buildClassDeclaration {
            name = "Child"
            classKind = ClassKind.ABSTRACT
            superType = superClass.type
            implementedTypes.add(intfClass.type)
        }
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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val superName = "Parent"
        val superClass = buildClassDeclaration {
            name = superName
            classKind = ClassKind.ABSTRACT
        }
        val functionName = "func"
        val function = buildFunctionDeclaration {
            name = functionName
            containingClassName = superName
            parameterList = buildParameterList()
        }
        superClass.functions.add(function)
        val intfName = "I0"
        val intfClass = buildClassDeclaration {
            name = intfName
            classKind = ClassKind.INTERFACE
        }
        val functionInIntf = buildFunctionDeclaration {
            name = functionName
            containingClassName = superName
            body = buildBlock()
            parameterList = buildParameterList()
        }
        intfClass.functions.add(functionInIntf)
        val subClass = buildClassDeclaration {
            name = "Child"
            classKind = ClassKind.FINAL
            superType = superClass.type
            implementedTypes.add(intfClass.type)
        }
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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val superName = "GrandParent"
        val superClass = buildClassDeclaration {
            name = superName
            classKind = ClassKind.ABSTRACT
        }
        val functionName = "func"
        val function = buildFunctionDeclaration {
            name = functionName
            containingClassName = superName
            body = buildBlock()
            parameterList = buildParameterList()
        }
        superClass.functions.add(function)

        val subClass = buildClassDeclaration {
            name = "Parent"
            classKind = ClassKind.FINAL
            superType = superClass.type
        }
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
        val intfClass = buildClassDeclaration {
            name = intfName
            classKind = ClassKind.INTERFACE
        }
        val functionInIntf = buildFunctionDeclaration {
            name = functionName
            containingClassName = intfName
            body = buildBlock()
            parameterList = buildParameterList()
        }
        intfClass.functions.add(functionInIntf)

        val subSubName = "Child"
        val subSubClass = buildClassDeclaration {
            name = subSubName
            classKind = ClassKind.ABSTRACT
            superType = subClass.type
            implementedTypes.add(intfClass.type)
        }
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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)

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
        val i0 = buildClassDeclaration {
            name = "I0"
            classKind = ClassKind.INTERFACE
        }
        val i1 = buildClassDeclaration {
            name = "I1"
            classKind = ClassKind.INTERFACE
            implementedTypes.add(i0.type)
        }
        val grandP = buildClassDeclaration {
            name = "GrandP"
            classKind = ClassKind.OPEN
            implementedTypes.add(i0.type)
        }
        val absP = buildClassDeclaration {
            name = "AbsP"
            classKind = ClassKind.ABSTRACT
            superType = grandP.type
        }
        val openC = buildClassDeclaration {
            name = "OpenC"
            classKind = ClassKind.OPEN
            superType = absP.type
            implementedTypes.add(i1.type)
        }

        val funcName = "func"
        val funcInI0 = buildFunctionDeclaration {
            name = funcName
            containingClassName = i0.name
            parameterList = buildParameterList()
        }
        i0.functions.add(funcInI0)
        val funcInI1 = buildFunctionDeclaration {
            name = funcName
            containingClassName = i1.name
            isOverride = true
            body = buildBlock()
            override.add(funcInI0)
            parameterList = buildParameterList()
        }
        i1.functions.add(funcInI1)
        val funcInGrandP = buildFunctionDeclaration {
            name = funcName
            containingClassName = grandP.name
            isOverride = true
            body = buildBlock()
            override.add(funcInI0)
            parameterList = buildParameterList()
        }
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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val superClass = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.OPEN
        }
        val childClass = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.FINAL
            superType = superClass.type
        }

        val funcInSuper = buildFunctionDeclaration {
            name = "func"
            containingClassName = superClass.name
            isFinal = true
            body = buildBlock()
            parameterList = buildParameterList()
        }
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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val superClass = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.OPEN
        }
        val childClass = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.FINAL
            superType = superClass.type
        }

        val funcInSuper = buildFunctionDeclaration {
            name = "func"
            containingClassName = superClass.name
            isFinal = true
            isOverrideStub = true
            isOverride = true
            parameterList = buildParameterList()
        }
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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)

        val i0 = buildClassDeclaration {
            name = "I0"
            classKind = ClassKind.INTERFACE
        }
        val i1 = buildClassDeclaration {
            name = "I1"
            classKind = ClassKind.INTERFACE
            implementedTypes.add(i0.type)
        }

        val funcInI0 = buildFunctionDeclaration {
            name = "func"
            containingClassName = i0.name
            body = buildBlock()
            parameterList = buildParameterList()
        }
        val funcInI1 = buildFunctionDeclaration {
            name = "func"
            containingClassName = i1.name
            parameterList = buildParameterList()
            isOverride = true
            override.add(funcInI0)
        }

        i0.functions.add(funcInI0)
        i1.functions.add(funcInI1)

        val clazz = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.FINAL
        }
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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)

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
        val i0 = buildClassDeclaration { name = "I0"; classKind = ClassKind.INTERFACE }
        val i1 = buildClassDeclaration { name = "I1"; classKind = ClassKind.INTERFACE }
        val i2 = buildClassDeclaration { name = "I2"; classKind = ClassKind.INTERFACE }
        val p = buildClassDeclaration { name = "P"; classKind = ClassKind.OPEN }
        val c = buildClassDeclaration { name = "C"; classKind = ClassKind.FINAL }
        i1.implementedTypes.add(i0.type)
        i2.implementedTypes.add(i0.type)
        p.implementedTypes.add(i2.type)
        c.superType = p.type
        c.implementedTypes.add(i1.type)

        val funcInI0 = buildFunctionDeclaration {
            name = "func"
            containingClassName = i0.name
            parameterList = buildParameterList()
        }.apply {
            i0.functions.add(this)
        }
        val funcInI1 = buildFunctionDeclaration {
            name = "func"
            containingClassName = i1.name
            parameterList = buildParameterList()
            body = buildBlock()
            isOverride = true
            override.add(funcInI0)
        }.apply {
            i1.functions.add(this)
        }
        val funcInI2 = buildFunctionDeclaration {
            name = "func"
            containingClassName = i2.name
            parameterList = buildParameterList()
            body = buildBlock()
            isOverride = true
            override.add(funcInI0)
        }.apply {
            i2.functions.add(this)
        }

        val funcInP = buildFunctionDeclaration {
            name = "func"
            containingClassName = p.name
            parameterList = buildParameterList()
            body = buildBlock()
            isOverride = true
            isOverrideStub = true
            override.add(funcInI2)
        }.apply {
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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)

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
        val i0 = buildClassDeclaration { name = "I0"; classKind = ClassKind.INTERFACE }
        val i1 = buildClassDeclaration { name = "I1"; classKind = ClassKind.INTERFACE }
        i1.implementedTypes.add(i0.type)
        val p = buildClassDeclaration { name = "P"; classKind = ClassKind.OPEN }
        p.implementedTypes.add(i0.type)
        val c = buildClassDeclaration { name = "C"; classKind = ClassKind.FINAL }
        c.superType = p.type
        c.implementedTypes.add(i1.type)

        val funcInI0 = buildFunctionDeclaration {
            name = "func"
            containingClassName = i0.name
            parameterList = buildParameterList()
            body = buildBlock()
        }
        i0.functions.add(funcInI0)

        val funcInI1 = buildFunctionDeclaration {
            name = "func"
            containingClassName = i1.name
            parameterList = buildParameterList()
            isOverride = true
            override.add(funcInI0)
        }
        i1.functions.add(funcInI1)
        val funcInP = buildFunctionDeclaration {
            name = "func"
            containingClassName = i1.name
            parameterList = buildParameterList()
            isOverride = true
            isOverrideStub = true
            override.add(funcInI0)
            body = buildBlock()
        }
        p.functions.add(funcInP)

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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val i0 = buildClassDeclaration { name = "I0"; classKind = ClassKind.INTERFACE }
        val i1 = buildClassDeclaration { name = "I1"; classKind = ClassKind.INTERFACE }
        i1.implementedTypes.add(i0.type)
        val i2 = buildClassDeclaration { name = "I2"; classKind = ClassKind.INTERFACE }
        i2.implementedTypes.add(i0.type)
        val i3 = buildClassDeclaration { name = "I3"; classKind = ClassKind.INTERFACE }
        i3.implementedTypes.add(i1.type)
        i3.implementedTypes.add(i2.type)

        val funcInI0 = buildFunctionDeclaration {
            name = "func"
            containingClassName = i0.name
            parameterList = buildParameterList()
        }
        i0.functions.add(funcInI0)
        val funcInI1 = buildFunctionDeclaration {
            name = "func"
            containingClassName = i1.name
            parameterList = buildParameterList()
            isOverride = true
            override.add(funcInI0)
        }
        i1.functions.add(funcInI1)
        val funcInI2 = buildFunctionDeclaration {
            name = "func"
            containingClassName = i2.name
            parameterList = buildParameterList()
            isOverride = true
            body = buildBlock()
            override.add(funcInI0)
        }
        i2.functions.add(funcInI2)
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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val i0 = buildClassDeclaration { name = "I0"; classKind = ClassKind.INTERFACE }
        val i1 = buildClassDeclaration { name = "I1"; classKind = ClassKind.INTERFACE }
        val p = buildClassDeclaration { name = "P"; classKind = ClassKind.OPEN }
        p.implementedTypes.add(i0.type)
        val c = buildClassDeclaration { name = "C"; classKind = ClassKind.FINAL }
        c.superType = p.type
        c.implementedTypes.add(i1.type)

        val funcInI0 = buildFunctionDeclaration {
            name = "func"
            containingClassName = i0.name
            parameterList = buildParameterList()
            body = buildBlock()
        }
        i0.functions.add(funcInI0)
        val funcInI1 = buildFunctionDeclaration {
            name = "func"
            containingClassName = i1.name
            parameterList = buildParameterList()
        }
        i1.functions.add(funcInI1)
        val funcInP = buildFunctionDeclaration {
            name = "func"
            containingClassName = p.name
            parameterList = buildParameterList()
            body = buildBlock()
            isOverride = true
            isOverrideStub = true
            override.add(funcInI0)
        }
        p.functions.add(funcInP)

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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val gp = buildClassDeclaration {
            name = "GP"
            classKind = ClassKind.OPEN
        }
        val p = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.OPEN
            superType = gp.type
        }
        val c = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.OPEN
            superType = p.type
        }
        val funcInGp = buildFunctionDeclaration {
            name = "func"
            containingClassName = gp.name
            parameterList = buildParameterList()
            body = buildBlock()
        }.apply {
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
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
        val t2 = buildTypeParameter { name = "T2"; upperbound = IrAny }
        val gp = buildClassDeclaration {
            name = "GP"
            classKind = ClassKind.OPEN
            typeParameters.add(t0)
        }
        val p = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.OPEN
            typeParameters.add(t1)
            val rawGp = gp.type as IrParameterizedClassifier
            rawGp.putTypeArgument(t0, t1)
            superType = rawGp
            allSuperTypeArguments[IrTypeParameterName(t0.name)] = t0 to t1
        }
        val c = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.OPEN
            typeParameters.add(t2)
            val rawP = p.type as IrParameterizedClassifier
            rawP.putTypeArgument(t1, t2)
            superType = rawP
            allSuperTypeArguments[IrTypeParameterName(t1.name)] = t1 to t2
            allSuperTypeArguments[IrTypeParameterName(t0.name)] = t0 to t2
        }
        val funcInGp = buildFunctionDeclaration {
            name = "func"
            containingClassName = "GP"
            body = buildBlock()
            parameterList = buildParameterList()
            parameterList.parameters.add(buildParameter {
                name = "arg"
                type = t0
            })
        }
        gp.functions.add(funcInGp)
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

    @Test
    fun testOverrideWithCorrectParameterWhenSuperHasGeneric2() {
        /**
         * GP<T0>#
         * P<T1>: GP<T1>#
         * C<T2>: P<T2>#
         * # means implement function
         *
         * func(GP<T0>)
         */
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
        val t2 = buildTypeParameter { name = "T2"; upperbound = IrAny }
        val gp = buildClassDeclaration {
            name = "GP"
            classKind = ClassKind.OPEN
            typeParameters.add(t0)
        }
        val p = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.OPEN
            typeParameters.add(t1)
            val rawGp = gp.type as IrParameterizedClassifier
            rawGp.putTypeArgument(t0, t1)
            superType = rawGp
            allSuperTypeArguments[IrTypeParameterName(t0.name)] = t0 to t1
        }
        val c = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.OPEN
            typeParameters.add(t2)
            val rawP = p.type as IrParameterizedClassifier
            rawP.putTypeArgument(t1, t2)
            superType = rawP
            allSuperTypeArguments[IrTypeParameterName(t1.name)] = t1 to t2
            allSuperTypeArguments[IrTypeParameterName(t0.name)] = t0 to t2
        }

        val funcInGp = buildFunctionDeclaration {
            name = "func"
            containingClassName = gp.name
            body = buildBlock()
            parameterList = buildParameterList {
                parameters.add(buildParameter {
                    name = "arg"
                    type = gp.type.apply {
                        this as IrParameterizedClassifier
                        putTypeArgument(t0, t0)
                    }
                })
            }
        }
        gp.functions.add(funcInGp)

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
        val gpWithT1 = gp.type.apply {
            this as IrParameterizedClassifier
            putTypeArgument(t0, t1)
        }
        funcInP.assertParameters(
            listOf(
                "arg" to gpWithT1
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
        val gpWithT2 = gp.type.apply {
            this as IrParameterizedClassifier
            putTypeArgument(t0, t2)
        }
        funcInC.assertParameters(
            listOf(
                "arg" to gpWithT2
            )
        )
    }

    @Test
    fun testOverrideWithCorrectParameterWhenSuperHasGeneric3() {
        /**
         * GP<T0>#
         * P<T1>: GP<T1>#
         * C<T2>: P<T2>#
         * GC<T3>: C<T3>#
         * # means implement function
         *
         * func(GP<T0>)
         */
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
        val t2 = buildTypeParameter { name = "T2"; upperbound = IrAny }
        val t3 = buildTypeParameter { name = "T3"; upperbound = IrAny }
        val gp = buildClassDeclaration {
            name = "GP"
            classKind = ClassKind.OPEN
            typeParameters.add(t0)
        }
        val p = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.OPEN
            typeParameters.add(t1)
            val rawGp = gp.type as IrParameterizedClassifier
            rawGp.putTypeArgument(t0, t1)
            superType = rawGp
            allSuperTypeArguments[IrTypeParameterName(t0.name)] = t0 to t1
        }
        val c = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.OPEN
            typeParameters.add(t2)
            val rawP = p.type as IrParameterizedClassifier
            rawP.putTypeArgument(t1, t2)
            superType = rawP
            allSuperTypeArguments[IrTypeParameterName(t1.name)] = t1 to t2
            allSuperTypeArguments[IrTypeParameterName(t0.name)] = t0 to t2
        }
        val gc = buildClassDeclaration {
            name = "GC"
            classKind = ClassKind.OPEN
            typeParameters.add(t3)
            val rawC = c.type as IrParameterizedClassifier
            rawC.putTypeArgument(t2, t3)
            superType = rawC
            allSuperTypeArguments[IrTypeParameterName(t2.name)] = t2 to t3
            allSuperTypeArguments[IrTypeParameterName(t1.name)] = t1 to t3
            allSuperTypeArguments[IrTypeParameterName(t0.name)] = t0 to t3
        }

        val funcInGp = buildFunctionDeclaration {
            name = "func"
            containingClassName = gp.name
            body = buildBlock()
            parameterList = buildParameterList {
                parameters.add(buildParameter {
                    name = "arg"
                    type = gp.type.apply {
                        this as IrParameterizedClassifier
                        putTypeArgument(t0, t0)
                    }
                })
            }
        }
        gp.functions.add(funcInGp)

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
        val gpWithT1 = gp.type.apply {
            this as IrParameterizedClassifier
            putTypeArgument(t0, t1)
        }
        funcInP.assertParameters(
            listOf(
                "arg" to gpWithT1
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
        val gpWithT2 = gp.type.apply {
            this as IrParameterizedClassifier
            putTypeArgument(t0, t2)
        }
        funcInC.assertParameters(
            listOf(
                "arg" to gpWithT2
            )
        )

        with(generator) {
            gc.genOverrides()
        }
        val funcInGC = gc.functions.single()
        funcInGC.assertIsOverride(
            listOf(funcInC),
            shouldBeSameSignature = false,
            shouldHasBody = true,
            shouldBeStub = true,
            shouldBeFinal = false
        )
        val gpWithT3 = gp.type.apply {
            this as IrParameterizedClassifier
            putTypeArgument(t0, t3)
        }
        funcInGC.assertParameters(
            listOf(
                "arg" to gpWithT3
            )
        )
    }

    @Test
    fun testOverrideWithCorrectParameterWhenSuperHasGeneric4() {
        /**
         * P<T0, T1>
         * C<T2, T3>: P<T2, T3>#
         * # means implement function
         *
         * func(P<T1, T0>)
         */
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
        val t2 = buildTypeParameter { name = "T2"; upperbound = IrAny }
        val t3 = buildTypeParameter { name = "T3"; upperbound = IrAny }
        val p = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.OPEN
            typeParameters.add(t0)
            typeParameters.add(t1)
        }
        val c = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.OPEN
            typeParameters.add(t2)
            typeParameters.add(t3)
            val rawP = p.type as IrParameterizedClassifier
            rawP.putTypeArgument(t0, t2)
            rawP.putTypeArgument(t1, t3)
            superType = rawP
            allSuperTypeArguments[IrTypeParameterName(t0.name)] = t0 to t2
            allSuperTypeArguments[IrTypeParameterName(t1.name)] = t1 to t3
        }

        val funcInP = buildFunctionDeclaration {
            name = "func"
            containingClassName = p.name
            body = buildBlock()
            val rawP = p.type as IrParameterizedClassifier
            rawP.putTypeArgument(t0, t1)
            rawP.putTypeArgument(t1, t0)
            parameterList = buildParameterList {
                parameters.add(buildParameter {
                    name = "arg"
                    type = buildNullableType {
                        innerType = rawP
                    }
                })
            }
        }
        p.functions.add(funcInP)

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
        val pWithT2T3 = p.type.apply {
            this as IrParameterizedClassifier
            putTypeArgument(t0, t3)
            putTypeArgument(t1, t2)
        }
        funcInC.assertParameters(
            listOf(
                "arg" to buildNullableType {  innerType = pWithT2T3 }
            )
        )
    }

    @Test
    fun testOverrideMultiSuperWithCorrectParameter() {
        /**
         * I<T0>&
         * P: I<P>#
         * C: P, I<P>^
         * & means abstract function
         * # means implement function
         * ^ means stub function
         *
         * func(I<T0>)
         */
        val generator = IrDeclGenerator(GeneratorConfig.testDefault)
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val i = buildClassDeclaration {
            name = "I"
            classKind = ClassKind.INTERFACE
            typeParameters.add(t0)
        }
        val p = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.OPEN
        }.apply {
            val rawI = i.type as IrParameterizedClassifier
            val pType = type
            rawI.putTypeArgument(t0, pType)
            implementedTypes.add(rawI)
            allSuperTypeArguments[IrTypeParameterName(t0.name)] = t0 to pType
        }
        val c = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.OPEN
            superType = p.type
            val rawI = i.type as IrParameterizedClassifier
            val pType = p.type
            rawI.putTypeArgument(t0, pType)
            implementedTypes.add(rawI)
            allSuperTypeArguments[IrTypeParameterName(t0.name)] = t0 to pType
        }
        val funcInI = buildFunctionDeclaration {
            name = "func"
            containingClassName = i.name
            val rawI = i.type as IrParameterizedClassifier
            rawI.putTypeArgument(t0, t0)
            parameterList = buildParameterList {
                parameters.add(buildParameter {
                    name = "arg0"
                    type = rawI
                })
            }
        }
        i.functions.add(funcInI)

        with(generator) {
            p.genOverrides()
        }
        val funcInP = p.functions.single()
        funcInP.assertIsOverride(
            listOf(funcInI),
            shouldBeSameSignature = false,
            shouldHasBody = true,
            shouldBeStub = false,
            shouldBeFinal = false
        )
        val iOfP = i.type.apply {
            this as IrParameterizedClassifier
            putTypeArgument(t0, p.type)
        }
        funcInP.assertParameters(
            listOf(
                "arg0" to iOfP
            )
        )

        with(generator) {
            c.genOverrides()
        }
        val funcInC = c.functions.single()
        funcInC.assertIsOverride(
            listOf(funcInI, funcInP),
            shouldBeSameSignature = false,
            shouldHasBody = true,
            shouldBeStub = true,
            shouldBeFinal = false
        )
        funcInC.assertParameters(
            listOf(
                "arg0" to iOfP
            )
        )
    }
    //</editor-fold>
    //</editor-fold>
}