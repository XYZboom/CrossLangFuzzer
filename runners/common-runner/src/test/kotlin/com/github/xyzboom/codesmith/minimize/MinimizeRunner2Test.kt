package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.minimize.MinimizeRunner2.Companion.buildClosures
import com.github.xyzboom.codesmith.minimize.MinimizeRunner2.Companion.closureOf
import com.github.xyzboom.codesmith.minimize.MinimizeRunner2.Companion.buildClosure
import com.github.xyzboom.codesmith.minimize.MinimizeRunner2.Companion.superTypeOf
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MinimizeRunner2Test {
    @Test
    fun superClassShouldNotInClosure() {
        val classA = buildClassDeclaration {
            classKind = ClassKind.INTERFACE
            name = "A"
        }
        val typeA = classA.type
        val classB = buildClassDeclaration {
            classKind = ClassKind.INTERFACE
            name = "B"
            implementedTypes += typeA
        }
        with(buildProgram {
            classes += classA
            classes += classB
        }) {
            val closureB = classB.closureOf()
            closureB shouldBe setOf(classB)
            val closures = buildClosures()
            closures shouldBe setOf(
                buildClosure(setOf(classA)),
                buildClosure(setOf(classB)),
                buildClosure(setOf(classA, classB, superTypeOf(classB, classA)))
            )
        }
    }

    @Test
    fun closureOfMember() {
        val classA = buildClassDeclaration {
            classKind = ClassKind.INTERFACE
            name = "A"
        }
        val funcInA = buildFunctionDeclaration {
            name = "func"
            containingClassName = classA.name
            parameterList = buildParameterList()
        }
        classA.functions.add(funcInA)
        with(buildProgram {
            classes += classA
        }) {
            val closureA = classA.closureOf()
            closureA shouldBe setOf(classA)
            val closureFA = funcInA.closureOf()
            closureFA shouldBe setOf(classA, funcInA)
            val closures = buildClosures()
            closures shouldBe setOf(
                buildClosure(setOf(classA)),
                buildClosure(setOf(classA, funcInA))
            )
        }
    }

    @Test
    fun deepInherit() {
        val classA = buildClassDeclaration {
            classKind = ClassKind.INTERFACE
            name = "A"
        }
        val typeA = classA.type
        val classB = buildClassDeclaration {
            classKind = ClassKind.INTERFACE
            name = "B"
            implementedTypes += typeA
        }
        val typeB = classB.type
        val classC = buildClassDeclaration {
            classKind = ClassKind.INTERFACE
            name = "C"
            implementedTypes += typeB
        }
        with(buildProgram {
            classes += classA
            classes += classB
            classes += classC
        }) {
            val closures = buildClosures()
            closures shouldBe setOf(
                buildClosure(setOf(classA)),
                buildClosure(setOf(classB)),
                buildClosure(setOf(classC)),
                buildClosure(setOf(classA, classB, superTypeOf(classB, classA))),
                buildClosure(setOf(classB, classC, superTypeOf(classC, classB))),
                buildClosure(setOf(
                    classA, classB, superTypeOf(classC, classB),
                    classC,
                    // C <: B, B <: A, so C <: A
                    superTypeOf(classC, classA),
                ))
            )
        }
    }
}