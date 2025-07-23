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
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock

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
            prog.removeClass(parent, "A0")
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
            prog.removeClass(parent, "A0")
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
}