package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.MockCompilerRunner
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import com.github.xyzboom.codesmith.assertIsOverride
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.type

class ClassLevelMinimizeRunnerTest {
    @Test
    fun testRemoveClassFromProg0() {
        /**
         * ```
         * interface P {
         *     fun func()
         * }
         * class C : P {
         *     override fun func()
         * }
         * ```
         */
        val minimizer = ClassLevelMinimizeRunner(MockCompilerRunner)
        val parent = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.INTERFACE
        }
        val child = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.OPEN
        }
        val funcInP = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            containingClassName = parent.name
        }
        parent.functions.add(funcInP)
        val funcInC = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            override.add(funcInP)
            containingClassName = child.name
            body = buildBlock()
        }
        child.functions.add(funcInC)
        val prog = ClassLevelMinimizeRunner.ProgramWithRemovedDecl(buildProgram {
            classes.add(parent)
            classes.add(child)
        })
        with(minimizer) {
            prog.replaceClass(parent.name, "A0")
        }
        prog.classes.size shouldBe 1
        prog.classes.single() shouldBeSameInstanceAs child
        child.functions.size shouldBe 0
    }

    @Test
    fun testRemoveClassFromProg1() {
        /**
         * ```
         * interface P {
         *     fun func()
         * }
         * interface P1 {
         *     fun func()
         * }
         * class C : P, P1 {
         *     override fun func()
         * }
         * ```
         */
        val minimizer = ClassLevelMinimizeRunner(MockCompilerRunner)
        val parent = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.INTERFACE
        }
        val parent1 = buildClassDeclaration {
            name = "P1"
            classKind = ClassKind.INTERFACE
        }
        val child = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.OPEN
        }
        val funcInP = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            containingClassName = parent.name
        }
        parent.functions.add(funcInP)
        val funcInP1 = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            containingClassName = parent1.name
        }
        parent1.functions.add(funcInP1)
        val funcInC = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            isOverride = true
            override.add(funcInP)
            override.add(funcInP1)
            containingClassName = child.name
            body = buildBlock()
        }
        child.functions.add(funcInC)
        val prog = ClassLevelMinimizeRunner.ProgramWithRemovedDecl(buildProgram {
            classes.add(parent)
            classes.add(parent1)
            classes.add(child)
        })
        with(minimizer) {
            prog.replaceClass(parent.name, "A0")
        }
        prog.classes.size shouldBe 2
        prog.classes shouldBeEqual listOf(parent1, child)
        parent1.functions.size shouldBe 1
        child.functions.size shouldBe 1
        val func = child.functions.single()
        func.assertIsOverride(
            listOf(funcInP1),
            true, shouldHasBody = true, shouldBeStub = false
        )
    }

    @Test
    fun testRemoveClassLevelTypeParamFromProg0() {
        /**
         * before we remove `T` from `P`
         * ```kt
         * class A {}
         * interface P<T> {
         *     fun func(t: T)
         * }
         * interface P1: P<A> {
         *     override fun func(t: A)
         * }
         * class C : P1 {
         *     override fun func(t: A)
         * }
         * ```
         * After:
         * ```kt
         * class A {}
         * interface P {
         *     fun func(t: Any)
         * }
         * interface P1: P {
         *     override fun func(t: Any)
         * }
         * class C : P1 {
         *     override fun func(t: Any)
         * }
         * ```
         */
        val minimizer = ClassLevelMinimizeRunner(MockCompilerRunner)
        val classA = buildClassDeclaration {
            name = "A"
            classKind = ClassKind.FINAL
        }
        val t = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val parent = buildClassDeclaration {
            name = "P"
            classKind = ClassKind.INTERFACE
            typeParameters.add(t)
        }
        val parent1 = buildClassDeclaration {
            name = "P1"
            classKind = ClassKind.INTERFACE
            implementedTypes.add(parent.type)
            allSuperTypeArguments[IrTypeParameterName(t.name)] = t to classA.type
        }
        val child = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.OPEN
            implementedTypes.add(parent1.type)
            allSuperTypeArguments[IrTypeParameterName(t.name)] = t to classA.type
        }
        val funcInP = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            parameterList.parameters.add(buildParameter {
                name = "t"
                type = t
            })
            containingClassName = parent.name
        }
        parent.functions.add(funcInP)
        val funcInP1 = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            containingClassName = parent1.name
            parameterList.parameters.add(buildParameter {
                name = "t"
                type = t
            })
        }
        parent1.functions.add(funcInP1)
        val funcInC = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            isOverride = true
            override.add(funcInP1)
            containingClassName = child.name
            body = buildBlock()
            parameterList.parameters.add(buildParameter {
                name = "t"
                type = t
            })
        }
        child.functions.add(funcInC)
        val prog = ClassLevelMinimizeRunner.ProgramWithRemovedDecl(buildProgram {
            classes.add(parent)
            classes.add(parent1)
            classes.add(child)
        })
        with(minimizer) {
            prog.replaceTypeParameterWithIrAny(IrTypeParameterName(t.name))
        }
        prog.classes.size shouldBe 3
        prog.classes shouldBeEqual listOf(parent, parent1, child)
        parent.functions.size shouldBe 1
        parent1.functions.size shouldBe 1
        child.functions.size shouldBe 1
        val func = child.functions.single()
        parent.typeParameters.size shouldBe 0
        func.assertIsOverride(
            listOf(funcInP1),
            true, shouldHasBody = true, shouldBeStub = false
        )
        val parameter = func.parameterList.parameters.single()
        parameter.type shouldBe IrAny
    }
}