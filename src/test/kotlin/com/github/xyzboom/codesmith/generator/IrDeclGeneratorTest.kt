package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.type
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldMatchExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.random.Random

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

    @Nested
    inner class FunctionReturnType {
        @Test
        fun testGenFunctionReturnType0() {
            val generator = SequentialTypeSelectionIrDeclGenerator(
                listOf(IrAny, IrAny), GeneratorConfig(functionReturnTypeNullableProbability = 0.5f),
                object : Random() {
                    // 0.7: do not make return type nullable
                    // 0.3: make return type nullable
                    private val values = listOf(0.7f, 0.3f).iterator()
                    override fun nextBits(bitCount: Int): Int {
                        throw IllegalStateException("should not be called")
                    }

                    override fun nextFloat(): Float {
                        return values.next()
                    }
                }
            )
            val prog = mockProgram
            val func = buildFunctionDeclaration {
                name = "func"
                parameterList = buildParameterList()
            }
            generator.genFunctionReturnType(
                prog, null, func
            )
            func.returnType shouldBe IrTypeMatcher(IrAny)
            generator.genFunctionReturnType(
                prog, null, func
            )
            func.returnType shouldBe IrTypeMatcher(buildNullableType { innerType = IrAny })
        }

        @Test
        fun genFunctionReturnTypeShouldChooseFromCorrectTypes0() {
            val prog = mockProgram
            val clazz = buildClassDeclaration {
                name = "MyClass"
                classKind = ClassKind.OPEN
            }
            prog.classes.add(clazz)
            val func = buildFunctionDeclaration {
                name = "func"
                parameterList = buildParameterList()
            }
            var called = 0
            val generator = object : IrDeclGenerator() {
                override fun randomType(
                    fromClasses: List<IrClassDeclaration>,
                    fromTypeParameters: List<IrTypeParameter>,
                    finishTypeArguments: Boolean,
                    filter: (IrType) -> Boolean
                ): IrType? {
                    called++
                    fromClasses.single() shouldBeSameInstanceAs clazz
                    fromTypeParameters.isEmpty().shouldBeTrue()
                    finishTypeArguments.shouldBeTrue()
                    return null
                }
            }
            generator.genFunctionReturnType(prog, null, func)
            called.shouldBe(1)
        }

        @Test
        fun genFunctionReturnTypeShouldChooseFromCorrectTypes1() {
            val prog = mockProgram
            val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
            val t2 = buildTypeParameter { name = "T2"; upperbound = IrAny }
            val clazz = buildClassDeclaration {
                name = "MyClass"
                classKind = ClassKind.OPEN
                typeParameters.add(t1)
            }
            prog.classes.add(clazz)
            val func = buildFunctionDeclaration {
                name = "func"
                parameterList = buildParameterList()
                typeParameters.add(t2)
            }
            var called = 0
            val generator = object : IrDeclGenerator() {
                override fun randomType(
                    fromClasses: List<IrClassDeclaration>,
                    fromTypeParameters: List<IrTypeParameter>,
                    finishTypeArguments: Boolean,
                    filter: (IrType) -> Boolean
                ): IrType? {
                    called++
                    fromClasses.single() shouldBeSameInstanceAs clazz
                    fromTypeParameters.size shouldBe 2
                    val sortedFrom = fromTypeParameters.sortedBy { it.name }
                    sortedFrom[0] shouldBe IrTypeMatcher(t1)
                    sortedFrom[1] shouldBe IrTypeMatcher(t2)
                    finishTypeArguments.shouldBeTrue()
                    return null
                }
            }
            generator.genFunctionReturnType(prog, clazz, func)
            called.shouldBe(1)
        }

        @Test
        fun genFunctionReturnTypeShouldChooseFromCorrectTypes2() {
            val prog = mockProgram
            val clazz = buildClassDeclaration {
                name = "MyClass"
                classKind = ClassKind.OPEN
            }
            prog.classes.add(clazz)
            val func = buildFunctionDeclaration {
                name = "func"
                parameterList = buildParameterList()
            }
            var called = 0
            val generator = object : IrDeclGenerator(
                config = GeneratorConfig(
                    allowNothingInReturnType = false
                )
            ) {
                override fun randomType(
                    fromClasses: List<IrClassDeclaration>,
                    fromTypeParameters: List<IrTypeParameter>,
                    finishTypeArguments: Boolean,
                    filter: (IrType) -> Boolean
                ): IrType? {
                    called++
                    fromClasses.single() shouldBeSameInstanceAs clazz
                    fromTypeParameters.isEmpty().shouldBeTrue()
                    finishTypeArguments.shouldBeTrue()
                    filter(IrNothing).shouldBeFalse()
                    filter(IrAny).shouldBeTrue()
                    filter(clazz.type).shouldBeTrue()
                    return null
                }
            }
            generator.genFunctionReturnType(prog, null, func)
            called.shouldBe(1)
        }
    }

    @Nested
    inner class FunctionParameter {
        @Test
        fun testGenFunctionParameter0() {
            val generator = SequentialTypeSelectionIrDeclGenerator(
                listOf(IrAny, IrAny), GeneratorConfig(functionParameterNullableProbability = 0.5f),
                object : Random() {
                    // 0.7: do not make return type nullable
                    // 0.3: make return type nullable
                    private val values = listOf(0.7f, 0.3f).iterator()
                    override fun nextBits(bitCount: Int): Int {
                        throw IllegalStateException("should not be called")
                    }

                    override fun nextFloat(): Float {
                        return values.next()
                    }
                }
            )
            val prog = mockProgram
            val func = buildFunctionDeclaration {
                name = "func"
                parameterList = buildParameterList()
            }
            val param1 = generator.genFunctionParameter(
                prog, null, func.typeParameters, "param1"
            )
            param1.name shouldBe "param1"
            param1.type shouldBe IrTypeMatcher(IrAny)
            val param2 = generator.genFunctionParameter(
                prog, null, func.typeParameters, "param2"
            )
            param2.name shouldBe "param2"
            param2.type shouldBe IrTypeMatcher(buildNullableType { innerType = IrAny })
        }

        @Test
        fun genFunctionParameterShouldChooseFromCorrectTypes0() {
            val prog = mockProgram
            val clazz = buildClassDeclaration {
                name = "MyClass"
                classKind = ClassKind.OPEN
            }
            prog.classes.add(clazz)
            val func = buildFunctionDeclaration {
                name = "func"
                parameterList = buildParameterList()
            }
            var called = 0
            val generator = object : IrDeclGenerator() {
                override fun randomType(
                    fromClasses: List<IrClassDeclaration>,
                    fromTypeParameters: List<IrTypeParameter>,
                    finishTypeArguments: Boolean,
                    filter: (IrType) -> Boolean
                ): IrType? {
                    called++
                    fromClasses.single() shouldBeSameInstanceAs clazz
                    fromTypeParameters.isEmpty().shouldBeTrue()
                    finishTypeArguments.shouldBeTrue()
                    return null
                }
            }
            generator.genFunctionParameter(prog, null, func.typeParameters, "param1")
            called.shouldBe(1)
        }

        @Test
        fun genFunctionParameterShouldChooseFromCorrectTypes1() {
            val prog = mockProgram
            val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
            val t2 = buildTypeParameter { name = "T2"; upperbound = IrAny }
            val clazz = buildClassDeclaration {
                name = "MyClass"
                classKind = ClassKind.OPEN
                typeParameters.add(t1)
            }
            prog.classes.add(clazz)
            val func = buildFunctionDeclaration {
                name = "func"
                parameterList = buildParameterList()
                typeParameters.add(t2)
            }
            var called = 0
            val generator = object : IrDeclGenerator() {
                override fun randomType(
                    fromClasses: List<IrClassDeclaration>,
                    fromTypeParameters: List<IrTypeParameter>,
                    finishTypeArguments: Boolean,
                    filter: (IrType) -> Boolean
                ): IrType? {
                    called++
                    fromClasses.single() shouldBeSameInstanceAs clazz
                    fromTypeParameters.size shouldBe 2
                    val sortedFrom = fromTypeParameters.sortedBy { it.name }
                    sortedFrom[0] shouldBe IrTypeMatcher(t1)
                    sortedFrom[1] shouldBe IrTypeMatcher(t2)
                    finishTypeArguments.shouldBeTrue()
                    return clazz.type
                }
            }
            generator.genFunctionParameter(prog, clazz, func.typeParameters, "param1")
            called.shouldBe(1)
        }

        @Test
        fun genFunctionParameterShouldChooseFromCorrectTypes2() {
            val prog = mockProgram
            val clazz = buildClassDeclaration {
                name = "MyClass"
                classKind = ClassKind.OPEN
            }
            prog.classes.add(clazz)
            val func = buildFunctionDeclaration {
                name = "func"
                parameterList = buildParameterList()
            }
            var called = 0
            val generator = object : IrDeclGenerator(
                config = GeneratorConfig(
                    allowNothingInParameter = false
                )
            ) {
                override fun randomType(
                    fromClasses: List<IrClassDeclaration>,
                    fromTypeParameters: List<IrTypeParameter>,
                    finishTypeArguments: Boolean,
                    filter: (IrType) -> Boolean
                ): IrType? {
                    called++
                    fromClasses.single() shouldBeSameInstanceAs clazz
                    fromTypeParameters.isEmpty().shouldBeTrue()
                    finishTypeArguments.shouldBeTrue()
                    filter(IrNothing).shouldBeFalse()
                    filter(IrAny).shouldBeTrue()
                    filter(clazz.type).shouldBeTrue()
                    return null
                }
            }
            generator.genFunctionParameter(prog, clazz, func.typeParameters, "param1")
            called.shouldBe(1)
        }
    }


}