package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.ir.types.IrVariance
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.type
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldMatchExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.random.Random

class IrDeclGeneratorTest {
    val mockProgram
        get() = buildProgram {
            modules.add(buildModule { name = "main" })
        }

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
        prog.modules.single().classes.add(p)
        val c = buildClassDeclaration {
            name = "C"
            classKind = ClassKind.FINAL
            typeParameters.add(t1)
        }
        prog.modules.single().classes.add(c)

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
        prog.modules.single().classes.add(p)
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
        prog.modules.single().classes.add(c)
        val gc = buildClassDeclaration {
            name = "GC"
            classKind = ClassKind.FINAL
            typeParameters.add(t2)
        }
        prog.modules.single().classes.add(gc)
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
            prog.modules.single().classes.add(clazz)
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
            prog.modules.single().classes.add(clazz)
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
            prog.modules.single().classes.add(clazz)
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
                listOf(IrAny, IrAny), GeneratorConfig(
                    functionParameterNullableProbability = 0.5f,
                    functionParameterPlatformProbability = 0.0f
                ),
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
            prog.modules.single().classes.add(clazz)
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
            prog.modules.single().classes.add(clazz)
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
            prog.modules.single().classes.add(clazz)
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

    @Nested
    inner class GenTypeArg {

        /**
         * ```kt
         * class A<T0: /*type 0*/, T1: T0/*1*/>
         * class B
         * ```
         * call [IrDeclGenerator.genTypeArguments], the unfinished type is:
         * `A<B/*3*/, /*unfinished*/>`
         *
         * @param expectCallback to get [IrType] of the above classes and types, we use callback
         */
        fun testTemplate0(
            t0UpperboundNullable: Boolean,
            t1UpperboundNullable: Boolean,
            makeArgNullableIfCan: Boolean,
            expectCallback: (
                IrTypeParameter/*T0*/, IrTypeParameter/*T1*/,
                IrParameterizedClassifier/*A*/, IrType/*B*/
            ) -> IrType
        ) {
            val t0Upperbound = if (t0UpperboundNullable) {
                buildNullableType { innerType = IrAny }
            } else {
                IrAny
            }
            val t0 = buildTypeParameter { name = "T0"; upperbound = t0Upperbound }
            val t1Upperbound = if (t1UpperboundNullable) {
                buildNullableType { innerType = t0 }
            } else {
                t0
            }
            val t1 = buildTypeParameter { name = "T1"; upperbound = t1Upperbound }
            val classA = buildClassDeclaration {
                name = "A"
                classKind = ClassKind.FINAL
                typeParameters.add(t0)
                typeParameters.add(t1)
            }
            val classB = buildClassDeclaration {
                name = "B"
                classKind = ClassKind.OPEN
            }
            val generator = SequentialTypeSelectionIrDeclGenerator(
                listOf(classB.type),
                config = GeneratorConfig(
                    notNullTypeArgForNullableUpperboundProbability =
                        if (makeArgNullableIfCan) {
                            0f
                        } else {
                            1f
                        }
                )
            )
            val typeA = classA.type
            generator.genTypeArguments(
                listOf(classB),
                emptyList(),
                typeA as IrParameterizedClassifier
            )
            val expect = expectCallback(t0, t1, classA.type as IrParameterizedClassifier, classB.type)
            typeA shouldBe IrTypeMatcher(expect)
        }

        @Test
        fun genTemplate0TypeArgOfUpperbound0() {
            testTemplate0(
                t0UpperboundNullable = true,
                t1UpperboundNullable = true,
                makeArgNullableIfCan = true,
            ) { t0, t1, typeA, typeB ->
                typeA.putTypeArgument(t0, buildNullableType { innerType = typeB })
                typeA.putTypeArgument(t1, buildNullableType { innerType = typeB })
                typeA
            }
            testTemplate0(
                t0UpperboundNullable = true,
                t1UpperboundNullable = true,
                makeArgNullableIfCan = false,
            ) { t0, t1, typeA, typeB ->
                typeA.putTypeArgument(t0, typeB)
                typeA.putTypeArgument(t1, typeB)
                typeA
            }
        }

        @Test
        fun genTemplate0TypeArgOfUpperbound1() {
            testTemplate0(
                t0UpperboundNullable = true,
                t1UpperboundNullable = false,
                makeArgNullableIfCan = true,
            ) { t0, t1, typeA, typeB ->
                typeA.putTypeArgument(t0, buildNullableType { innerType = typeB })
                typeA.putTypeArgument(t1, buildNullableType { innerType = typeB })
                typeA
            }
            testTemplate0(
                t0UpperboundNullable = true,
                t1UpperboundNullable = false,
                makeArgNullableIfCan = false,
            ) { t0, t1, typeA, typeB ->
                typeA.putTypeArgument(t0, typeB)
                typeA.putTypeArgument(t1, typeB)
                typeA
            }
        }

        @Test
        fun genTemplate0TypeArgOfUpperbound2() {
            testTemplate0(
                t0UpperboundNullable = false,
                t1UpperboundNullable = true,
                makeArgNullableIfCan = true,
            ) { t0, t1, typeA, typeB ->
                typeA.putTypeArgument(t0, typeB)
                typeA.putTypeArgument(t1, buildNullableType { innerType = typeB })
                typeA
            }
            testTemplate0(
                t0UpperboundNullable = false,
                t1UpperboundNullable = true,
                makeArgNullableIfCan = false,
            ) { t0, t1, typeA, typeB ->
                typeA.putTypeArgument(t0, typeB)
                typeA.putTypeArgument(t1, typeB)
                typeA
            }
        }

        @Test
        fun genTemplate0TypeArgOfUpperbound3() {
            testTemplate0(
                t0UpperboundNullable = false,
                t1UpperboundNullable = false,
                makeArgNullableIfCan = true,
            ) { t0, t1, typeA, typeB ->
                typeA.putTypeArgument(t0, typeB)
                typeA.putTypeArgument(t1, typeB)
                typeA
            }
            testTemplate0(
                t0UpperboundNullable = false,
                t1UpperboundNullable = false,
                makeArgNullableIfCan = false,
            ) { t0, t1, typeA, typeB ->
                typeA.putTypeArgument(t0, typeB)
                typeA.putTypeArgument(t1, typeB)
                typeA
            }
        }

        @Test
        fun genTypeArgOfUpperbound0() {
            val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
            val t1 = buildTypeParameter { name = "T1"; upperbound = t0 }
            val classA = buildClassDeclaration {
                name = "A"
                classKind = ClassKind.FINAL
                typeParameters += t0
                typeParameters += t1
            }
            val t2 = buildTypeParameter { name = "T2"; upperbound = buildNullableType { innerType = IrAny } }
            val t3 = buildTypeParameter { name = "T3"; upperbound = t2 }
            val generator = FilteredSequentialTypeSelectionIrDeclGenerator(
                listOf(t3, buildNullableType { innerType = IrAny }),
                config = GeneratorConfig(
                    notNullTypeArgForNullableUpperboundProbability = 1f
                )
            )
            val typeA = classA.type
            generator.genTypeArguments(
                emptyList(),
                emptyList(),
                typeA as IrParameterizedClassifier
            )
            val expectA = classA.type.apply {
                this as IrParameterizedClassifier
                putTypeArgument(t0, IrAny)
                putTypeArgument(t1, IrAny)
            }
            typeA shouldBe IrTypeMatcher(expectA)
        }
    }

    @Nested
    inner class UseSiteVariance {
        private fun getAvailableTypeNames(): List<String> {
            val base = buildClassDeclaration {
                name = "Base"
                classKind = ClassKind.OPEN
            }
            val sub = buildClassDeclaration {
                name = "Sub"
                classKind = ClassKind.FINAL
                superType = base.type
            }
            val unrelated = buildClassDeclaration {
                name = "Unrelated"
                classKind = ClassKind.FINAL
            }
            val generic = buildClassDeclaration {
                name = "Generic"
                classKind = ClassKind.FINAL
                typeParameters += buildTypeParameter { name = "T"; upperbound = IrAny }
            }
            val candidates = listOf(
                base.type,
                sub.type,
                unrelated.type,
                IrAny,
                IrUnit,
                IrNothing,
                generic.type
            )
            return IrDeclGenerator().getAvailableTypesForPosition(
                candidates,
                base.type
            ).map { (it as IrClassifier).classDecl.name }
        }

        @Test
        fun `available concrete types must satisfy the effective upper bound`() {
            getAvailableTypeNames() shouldBe listOf("Base", "Sub")
        }

        @Test
        fun `available concrete types include in-scope type parameters`() {
            val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
            val availableTypes = IrDeclGenerator().getAvailableTypesForPosition(
                candidateTypes = listOf(t1),
                upperbound = IrAny
            )

            availableTypes.single() shouldBeSameInstanceAs t1
        }

        @Test
        fun `generic argument generation can select an enclosing class type parameter`() {
            val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
            val u = buildTypeParameter { name = "U"; upperbound = IrAny }
            val classB = buildClassDeclaration {
                name = "B"
                classKind = ClassKind.FINAL
                typeParameters += u
            }
            val generator = FilteredSequentialTypeSelectionIrDeclGenerator(listOf(t1))
            val typeB = classB.type as IrParameterizedClassifier

            generator.genTypeArguments(
                fromClasses = listOf(classB),
                fromTypeParameters = listOf(t1),
                targetType = typeB
            )

            val argument = typeB.arguments[IrTypeParameterName("U")]!!.second
            argument.variance shouldBe IrVariance.INVARIANT
            argument.type shouldBeSameInstanceAs t1
        }

        @Test
        fun `dependent generic argument can select a compatible enclosing type parameter`() {
            val base = buildClassDeclaration {
                name = "Base"
                classKind = ClassKind.OPEN
            }
            val sub = buildClassDeclaration {
                name = "Sub"
                classKind = ClassKind.FINAL
                superType = base.type
            }
            val t1 = buildTypeParameter { name = "T1"; upperbound = sub.type }
            val x = buildTypeParameter { name = "X"; upperbound = base.type }
            val y = buildTypeParameter { name = "Y"; upperbound = x }
            val classB = buildClassDeclaration {
                name = "B"
                classKind = ClassKind.FINAL
                typeParameters += x
                typeParameters += y
            }
            val generator = FilteredSequentialTypeSelectionIrDeclGenerator(
                listOf(base.type, t1)
            )
            val typeB = classB.type as IrParameterizedClassifier

            generator.genTypeArguments(
                fromClasses = listOf(base, sub, classB),
                fromTypeParameters = listOf(t1),
                targetType = typeB
            )

            val argument = typeB.arguments[IrTypeParameterName("Y")]!!.second
            argument.type shouldBeSameInstanceAs t1
        }

        @Test
        fun `test use-site star projection`() {
            val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
            val classBox = buildClassDeclaration {
                name = "Box"
                classKind = ClassKind.FINAL
                typeParameters += t0
            }
            val config = GeneratorConfig(
                enableUseSiteVariance = true,
                useSiteVarianceProbability = 1.0f,
                starProjectionProbability = 1.0f
            )
            val generator = IrDeclGenerator(config = config)
            val targetType = classBox.type as IrParameterizedClassifier
            generator.genTypeArguments(
                listOf(classBox),
                emptyList(),
                targetType,
                allowVariance = true
            )
            val arg = targetType.arguments[IrTypeParameterName("T0")]!!.second
            arg.variance shouldBe IrVariance.STAR
            arg.type shouldBe null
        }

        @Test
        fun `test use-site covariance`() {
            val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
            val classBox = buildClassDeclaration {
                name = "Box"
                classKind = ClassKind.FINAL
                typeParameters += t0
            }
            val config = GeneratorConfig(
                enableUseSiteVariance = true,
                useSiteVarianceProbability = 1.0f,
                starProjectionProbability = 0.0f,
                covariantProbability = 1.0f
            )
            val generator = IrDeclGenerator(config = config)
            val targetType = classBox.type as IrParameterizedClassifier
            generator.genTypeArguments(
                listOf(classBox),
                emptyList(),
                targetType,
                allowVariance = true
            )
            val arg = targetType.arguments[IrTypeParameterName("T0")]!!.second
            arg.variance shouldBe IrVariance.COVARIANT
            arg.type shouldBe IrAny
        }

        @Test
        fun `test use-site contravariance`() {
            val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
            val classBox = buildClassDeclaration {
                name = "Box"
                classKind = ClassKind.FINAL
                typeParameters += t0
            }
            val config = GeneratorConfig(
                enableUseSiteVariance = true,
                useSiteVarianceProbability = 1.0f,
                starProjectionProbability = 0.0f,
                covariantProbability = 0.0f
            )
            val generator = IrDeclGenerator(config = config)
            val targetType = classBox.type as IrParameterizedClassifier
            generator.genTypeArguments(
                listOf(classBox),
                emptyList(),
                targetType,
                allowVariance = true
            )
            val arg = targetType.arguments[IrTypeParameterName("T0")]!!.second
            arg.variance shouldBe IrVariance.CONTRAVARIANT
            arg.type shouldBe IrAny
        }

        @Test
        fun `type parameter used as a later upper bound must stay invariant`() {
            val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
            val t1 = buildTypeParameter { name = "T1"; upperbound = t0 }
            val classBox = buildClassDeclaration {
                name = "Box"
                classKind = ClassKind.FINAL
                typeParameters += t0
                typeParameters += t1
            }
            val config = GeneratorConfig(
                enableUseSiteVariance = true,
                useSiteVarianceProbability = 1.0f,
                starProjectionProbability = 1.0f
            )
            val generator = SequentialTypeSelectionIrDeclGenerator(
                listOf(IrAny),
                config = config
            )
            val targetType = classBox.type as IrParameterizedClassifier
            generator.genTypeArguments(
                listOf(classBox),
                emptyList(),
                targetType,
                allowVariance = true
            )
            val firstArg = targetType.arguments[IrTypeParameterName("T0")]!!.second
            firstArg.variance shouldBe IrVariance.INVARIANT
            firstArg.type shouldBe IrAny
            val secondArg = targetType.arguments[IrTypeParameterName("T1")]!!.second
            secondArg.variance shouldBe IrVariance.STAR
            secondArg.type shouldBe null
        }

        @Test
        fun `supertype must always be invariant even if variance is enabled`() {
            val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
            val classP = buildClassDeclaration {
                name = "P"
                classKind = ClassKind.OPEN
                typeParameters += t0
            }
            val classC = buildClassDeclaration {
                name = "C"
                classKind = ClassKind.FINAL
            }
            val prog = mockProgram.apply {
                modules.single().classes += classP
                modules.single().classes += classC
            }
            val config = GeneratorConfig(
                enableUseSiteVariance = true,
                useSiteVarianceProbability = 1.0f,
                starProjectionProbability = 1.0f
            )
            val generator = SequentialTypeSelectionIrDeclGenerator(
                listOf(classP.type, IrAny), config = config
            )
            with(generator) {
                classC.genSuperTypes(prog)
            }
            val superType = classC.superType as IrParameterizedClassifier
            val arg = superType.arguments[IrTypeParameterName("T0")]!!.second
            arg.variance shouldBe IrVariance.INVARIANT
        }
    }
}