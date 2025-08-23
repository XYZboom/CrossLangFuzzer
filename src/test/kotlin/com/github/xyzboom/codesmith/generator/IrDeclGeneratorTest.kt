package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.type
import io.kotest.matchers.maps.shouldMatchExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

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
}