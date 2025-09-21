package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.TypeKind
import com.github.xyzboom.codesmith.ir.TypeKind.DNN
import com.github.xyzboom.codesmith.ir.TypeKind.NotNull
import com.github.xyzboom.codesmith.ir.TypeKind.Nullable
import com.github.xyzboom.codesmith.ir.TypeKind.Platform
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.render
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builder.buildDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildPlatformType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.ir.types.set
import com.github.xyzboom.codesmith.ir.types.type
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KtIrClassPrinterTest {
    companion object {
        private val todoFunctionBody = "${" ".repeat(8)}throw RuntimeException()\n"
        private val todoPropertyInitExpr = "TODO()"
    }

    //<editor-fold desc="Function">
    @Test
    fun testPrintSimpleClassWithSimpleFunction() {
        val printer = KtIrClassPrinter()
        val clazzName = "SimpleClassWithSimpleFunction"
        val funcName = "simple"
        val clazz = buildClassDeclaration {
            name = clazzName
            classKind = ClassKind.FINAL
        }
        val func = buildFunctionDeclaration {
            name = funcName
            isFinal = true
            containingClassName = clazzName
            body = buildBlock()
            parameterList = buildParameterList()
        }
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = "public class $clazzName {\n" +
                "    fun $funcName(): Unit {\n" +
                todoFunctionBody +
                "    }\n" +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintSimpleClassWithSimpleStubFunction() {
        val printer = KtIrClassPrinter()
        val clazzName = "SimpleClassWithSimpleFunction"
        val funcName = "simple"
        val clazz = buildClassDeclaration {
            name = clazzName
            classKind = ClassKind.FINAL
        }
        val func = buildFunctionDeclaration {
            name = funcName
            isFinal = true
            containingClassName = clazzName
            body = buildBlock()
            isOverride = true
            isOverrideStub = true
            parameterList = buildParameterList()
        }
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = "public class $clazzName {\n" +
                "    // stub\n" +
                "    /*\n" +
                "    override fun $funcName(): Unit {\n" +
                todoFunctionBody +
                "    }\n" +
                "    */\n" +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintSimpleClassWithFunctionHasParameter() {
        val printer = KtIrClassPrinter()
        val clazzName = "SimpleClassWithFunctionHasParameter"
        val funcName = "simple"
        val clazz = buildClassDeclaration {
            name = clazzName
            classKind = ClassKind.FINAL
        }
        val func = buildFunctionDeclaration {
            name = funcName
            isFinal = true
            containingClassName = clazzName
            body = buildBlock()
            parameterList = buildParameterList {
                parameters.add(buildParameter {
                    name = "arg0"
                    type = IrAny
                })
                parameters.add(buildParameter {
                    name = "arg1"
                    type = clazz.type
                })
            }
        }
        clazz.functions.add(func)
        val result = printer.print(clazz)
        val expect = "public class $clazzName {\n" +
                "    fun $funcName(arg0: Any, arg1: $clazzName): Unit {\n" +
                todoFunctionBody +
                "    }\n" +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintTypeParameterInFunction0() {
        /**
         * ```kt
         * open class A<T> {
         *     fun func(t: T) {}
         * }
         * class B : A<B> {
         *     fun func(t: B) {}
         * }
         * ```
         */
        val printer = KtIrClassPrinter()
        val t = buildTypeParameter { name = "T"; upperbound = IrAny }
        val classA = buildClassDeclaration {
            name = "A"
            classKind = ClassKind.OPEN
            typeParameters += t
        }
        val funcInA = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            parameterList.parameters.add(buildParameter {
                name = "t"
                type = t
            })
        }
        classA.functions.add(funcInA)
        val classB = buildClassDeclaration {
            name = "B"
            classKind = ClassKind.FINAL
        }
        classB.superType = buildParameterizedClassifier {
            classDecl = classA
            arguments = HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType?>>().apply {
                put(IrTypeParameterName(t.name), t to classB.type)
            }
        }
        classB.allSuperTypeArguments = HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>().apply {
            put(IrTypeParameterName(t.name), t to classB.type)
        }
        val funcInB = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            parameterList.parameters.add(buildParameter {
                name = "t"
                type = t
            })
        }
        classB.functions.add(funcInB)
        val result = printer.print(classB)
        val expect = "public class B : A<B>() {\n" +
                "    abstract open fun func(t: B): Unit\n" +
                "}\n"
        assertEquals(expect, result)
    }
    //</editor-fold>

    @Test
    fun testPrintTypeArg0() {
        /**
         * ```kt
         * class A<T0>
         * interface I<T1> {
         *     fun func(): A<T1>
         * }
         * class B : I<Any?> {
         *     fun func(): A<Any?> {}
         * }
         * ```
         */
        val printer = KtIrClassPrinter()
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
        val classA = buildClassDeclaration {
            name = "A"
            classKind = ClassKind.FINAL
            typeParameters += t0
        }
        val intfI = buildClassDeclaration {
            name = "I"
            classKind = ClassKind.INTERFACE
            typeParameters += t1
        }
        val funcInI = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            returnType = classA.type.apply {
                this as IrParameterizedClassifier
                putTypeArgument(t0, t1)
            }
        }
        intfI.functions.add(funcInI)
        val classB = buildClassDeclaration {
            name = "B"
            classKind = ClassKind.FINAL
            implementedTypes.add(intfI.type.apply {
                this as IrParameterizedClassifier
                putTypeArgument(t1, buildNullableType { innerType = IrAny })
            })
            allSuperTypeArguments = HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>().apply {
                set(t1, buildNullableType { innerType = IrAny })
            }
        }
        val funcInB = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            returnType = classA.type.apply {
                this as IrParameterizedClassifier
                putTypeArgument(t0, t1)
            }
            body = buildBlock()
            isOverride = true
            override.add(funcInI)
        }
        classB.functions.add(funcInB)
        val result = printer.print(classB)
        val expect = "public class B : I<Any?> {\n" +
                "    override open fun func(): A<Any?> {\n" +
                todoFunctionBody +
                "    }\n" +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintTypeArg1() {
        /**
         * ```kt
         * class A<T0>
         * interface I<T1> {
         *     fun func(): A<T1>
         * }
         * class B : I<Any?> {
         *     fun func(a: A<Any?>) {}
         * }
         * ```
         */
        val printer = KtIrClassPrinter()
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
        val classA = buildClassDeclaration {
            name = "A"
            classKind = ClassKind.FINAL
            typeParameters += t0
        }
        val intfI = buildClassDeclaration {
            name = "I"
            classKind = ClassKind.INTERFACE
            typeParameters += t1
        }
        val funcInI = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            parameterList.parameters.add(buildParameter {
                name = "a"
                type = classA.type.apply {
                    this as IrParameterizedClassifier
                    putTypeArgument(t0, t1)
                }
            })
        }
        intfI.functions.add(funcInI)
        val classB = buildClassDeclaration {
            name = "B"
            classKind = ClassKind.FINAL
            implementedTypes.add(intfI.type.apply {
                this as IrParameterizedClassifier
                putTypeArgument(t1, buildNullableType { innerType = IrAny })
            })
            allSuperTypeArguments = HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>().apply {
                set(t1, buildNullableType { innerType = IrAny })
            }
        }
        val funcInB = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            parameterList.parameters.add(buildParameter {
                name = "a"
                type = classA.type.apply {
                    this as IrParameterizedClassifier
                    putTypeArgument(t0, t1)
                }
            })
            body = buildBlock()
            isOverride = true
            override.add(funcInI)
        }
        classB.functions.add(funcInB)
        val result = printer.print(classB)
        val expect = "public class B : I<Any?> {\n" +
                "    override open fun func(a: A<Any?>): Unit {\n" +
                todoFunctionBody +
                "    }\n" +
                "}\n"
        assertEquals(expect, result)
    }

    @Nested
    inner class TypeParameterTest {
        //<editor-fold desc="Template0">
        /**
         * ```kt
         * open class A</*type 1*/> {
         *     abstract fun func(t: /*type 2*/)
         * }
         *
         * open class B : A</*type arg*/> { ... }
         * ```
         */
        private fun assertTemplate0(
            typeParameterUpperboundNullable: Boolean,
            funcParamNullable: TypeKind,
            expectClassA: String,
            expectClassBWithTypeArgAny: String,
            expectClassBWithTypeArgNullableAny: String? = null,
            expectClassBWithTypeArgPlatformAny: String? = null,
        ) {
            val printer = KtIrClassPrinter()
            val upperbound = if (typeParameterUpperboundNullable) {
                buildNullableType { innerType = IrAny }
            } else {
                IrAny
            }
            val t = buildTypeParameter { name = "T"; this.upperbound = upperbound }
            val funcParamType = when (funcParamNullable) {
                DNN -> buildDefinitelyNotNullType { innerType = t }
                Nullable -> buildNullableType { innerType = t }
                NotNull -> t
                Platform -> buildPlatformType { innerType = t }
            }
            val classA = buildClassDeclaration {
                name = "A"
                classKind = ClassKind.OPEN
                typeParameters += t
            }
            val func = buildFunctionDeclaration {
                name = "func"
                parameterList = buildParameterList()
                parameterList.parameters.add(buildParameter {
                    name = "t"
                    type = funcParamType
                })
                printNullableAnnotations = true
            }
            classA.functions.add(func)
            val resultA = printer.print(classA)
            assertEquals(expectClassA, resultA)

            run {
                /**
                 * ```kt
                 * class B : A<Any> {
                 *     override fun func(t: /*type 2*/)
                 * }
                 * ```
                 */
                val classB = buildClassDeclaration {
                    name = "B"
                    classKind = ClassKind.FINAL
                    superType = classA.type.apply {
                        this as IrParameterizedClassifier
                        putTypeArgument(t, IrAny)
                    }
                    allSuperTypeArguments = HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>().apply {
                        put(IrTypeParameterName(t.name), t to IrAny)
                    }
                }

                val funcInB = buildFunctionDeclaration {
                    name = "func"
                    parameterList = buildParameterList()
                    parameterList.parameters.add(buildParameter {
                        name = "t"
                        type = funcParamType
                    })
                    printNullableAnnotations = true
                }
                classB.functions.add(funcInB)
                val resultB = printer.print(classB)
                assertEquals(expectClassBWithTypeArgAny, resultB)
            }
            expectClassBWithTypeArgNullableAny?.let {
                /**
                 * ```kt
                 * class B : A<Any?> {
                 *     override fun func(t: /*type 2*/)
                 * }
                 * ```
                 */
                val classB = buildClassDeclaration {
                    name = "B"
                    classKind = ClassKind.FINAL
                    superType = classA.type.apply {
                        this as IrParameterizedClassifier
                        putTypeArgument(t, buildNullableType { innerType = IrAny })
                    }
                    allSuperTypeArguments = HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>().apply {
                        put(IrTypeParameterName(t.name), t to buildNullableType { innerType = IrAny })
                    }
                }

                val funcInB = buildFunctionDeclaration {
                    name = "func"
                    parameterList = buildParameterList()
                    parameterList.parameters.add(buildParameter {
                        name = "t"
                        type = funcParamType
                    })
                    printNullableAnnotations = true
                }
                classB.functions.add(funcInB)
                val resultB = printer.print(classB)
                assertEquals(it, resultB)
            }
            expectClassBWithTypeArgPlatformAny?.let {
                /**
                 * ```kt
                 * class B : A<Any!> {
                 *     override fun func(t: /*type 2*/)
                 * }
                 * ```
                 */
                val classB = buildClassDeclaration {
                    name = "B"
                    classKind = ClassKind.FINAL
                    superType = classA.type.apply {
                        this as IrParameterizedClassifier
                        putTypeArgument(t, buildPlatformType { innerType = IrAny })
                    }
                    allSuperTypeArguments = HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>().apply {
                        put(IrTypeParameterName(t.name), t to buildPlatformType { innerType = IrAny })
                    }
                }

                val funcInB = buildFunctionDeclaration {
                    name = "func"
                    parameterList = buildParameterList()
                    parameterList.parameters.add(buildParameter {
                        name = "t"
                        type = funcParamType
                    })
                    printNullableAnnotations = true
                }
                classB.functions.add(funcInB)
                val resultB = printer.print(classB)
                assertEquals(it, resultB)
            }
        }
        val NULLABILITY_ANNOTATION_IMPORTS = ""
        @Test
        fun testUpperbound0() {
            /**
             * ```kt
             * open class A<T> {
             *     abstract fun func(t: T)
             * }
             * ```
             */
            val expectA = "public open class A<T : Any?> {\n" +
                    "    abstract open fun func(t: T): Unit\n" +
                    "}\n"
            val expectAnyB = "public class B : A<Any>() {\n" +
                    "    abstract open fun func(t: Any): Unit\n" +
                    "}\n"
            val expectNullableAnyB = "public class B : A<Any?>() {\n" +
                    "    abstract open fun func(t: Any?): Unit\n" +
                    "}\n"
            assertTemplate0(
                typeParameterUpperboundNullable = true,
                funcParamNullable = NotNull,
                expectA,
                expectAnyB,
                expectNullableAnyB,
                expectAnyB
            )
        }

        @Test
        fun testUpperbound1() {
            /**
             * ```kt
             * open class A<T: Any> {
             *     abstract fun func(t: T)
             * }
             * ```
             */
            val expectA = "public open class A<T : Any> {\n" +
                    "    abstract open fun func(t: T): Unit\n" +
                    "}\n"
            val expectAnyB = "public class B : A<Any>() {\n" +
                    "    abstract open fun func(t: Any): Unit\n" +
                    "}\n"
            val expectNullableAnyB = "public class B : A<Any?>() {\n" +
                    "    abstract open fun func(t: Any?): Unit\n" +
                    "}\n"
            assertTemplate0(
                typeParameterUpperboundNullable = false,
                funcParamNullable = NotNull,
                expectA,
                expectAnyB,
                expectNullableAnyB,
                expectAnyB
            )
        }

        @Test
        fun testUpperbound2() {
            /**
             * ```kt
             * open class A<T> {
             *     abstract fun func(t: T?)
             * }
             * ```
             */
            val expectA = "public open class A<T : Any?> {\n" +
                    "    abstract open fun func(t: T?): Unit\n" +
                    "}\n"
            val expectAnyB = "public class B : A<Any>() {\n" +
                    "    abstract open fun func(t: Any?): Unit\n" +
                    "}\n"
            val expectNullableAnyB = "public class B : A<Any?>() {\n" +
                    "    abstract open fun func(t: Any?): Unit\n" +
                    "}\n"
            assertTemplate0(
                typeParameterUpperboundNullable = true,
                funcParamNullable = Nullable,
                expectA,
                expectAnyB,
                expectNullableAnyB,
                expectAnyB
            )
        }

        @Test
        fun testUpperbound3() {
            /**
             * ```kt
             * open class A<T: Any> {
             *     abstract fun func(t: T?)
             * }
             * ```
             */
            val expectA = "public open class A<T : Any> {\n" +
                    "    abstract open fun func(t: T?): Unit\n" +
                    "}\n"
            val expectAnyB = "public class B : A<Any>() {\n" +
                    "    abstract open fun func(t: Any?): Unit\n" +
                    "}\n"
            val expectNullableAnyB = "public class B : A<Any?>() {\n" +
                    "    abstract open fun func(t: Any?): Unit\n" +
                    "}\n"
            assertTemplate0(
                typeParameterUpperboundNullable = false,
                funcParamNullable = Nullable,
                expectA,
                expectAnyB,
                expectNullableAnyB,
                expectAnyB
            )
        }

        @Test
        fun testUpperbound4() {
            /**
             * ```kt
             * open class A<T: Any?> {
             *     abstract fun func(t: T & Any)
             * }
             * ```
             */
            val expectA = "public open class A<T : Any?> {\n" +
                    "    abstract open fun func(t: T & Any): Unit\n" +
                    "}\n"
            val expectAnyB = "public class B : A<Any>() {\n" +
                    "    abstract open fun func(t: Any): Unit\n" +
                    "}\n"
            val expectNullableAnyB = "public class B : A<Any?>() {\n" +
                    "    abstract open fun func(t: Any): Unit\n" +
                    "}\n"
            assertTemplate0(
                typeParameterUpperboundNullable = true,
                funcParamNullable = DNN,
                expectA,
                expectAnyB,
                expectNullableAnyB,
                expectAnyB
            )
        }

        @Test
        fun testUpperbound5() {
            /**
             * ```kt
             * open class A<T: Any?> {
             *     abstract fun func(t: T!)
             * }
             * ```
             */
            val expectA = "public open class A<T : Any?> {\n" +
                    "    abstract open fun func(t: T): Unit\n" +
                    "}\n"
            val expectAnyB = "public class B : A<Any>() {\n" +
                    "    abstract open fun func(t: Any): Unit\n" +
                    "}\n"
            val expectNullableAnyB = "public class B : A<Any?>() {\n" +
                    "    abstract open fun func(t: Any?): Unit\n" +
                    "}\n"
            assertTemplate0(
                typeParameterUpperboundNullable = true,
                funcParamNullable = Platform,
                expectA,
                expectAnyB,
                expectNullableAnyB,
                expectAnyB
            )
        }
        //</editor-fold>

        //<editor-fold desc="Template1">
        /**
         * ```kt
         * open class A<T0/*type 1*/, T1: T0/*type2*/> {
         *     abstract fun func(t: T1/*type 3*/)
         * }
         * ```
         */
        private fun assertTemplate1(
            t0UpperboundNullable: Boolean,
            t1UpperboundNullable: Boolean?,
            funcParamTypeKind: TypeKind,
            expectClassA: String,
            /**
             * ```kt
             * class B : A</*type arg 1*/, /*type arg 2*/>
             * ```
             * type arg 1: Any, Any?, Any!;
             * type arg 2: Any, Any?, Any!;
             * for example `expectClassB[1][2]` do the assertion of `class B : A<Any, Any!>`
             */
            expectClassB: Array<Array<String?>>,
        ) {
            val printer = KtIrClassPrinter()
            val t0Upperbound = if (t0UpperboundNullable) {
                buildNullableType { innerType = IrAny }
            } else {
                IrAny
            }
            val t0 = buildTypeParameter { name = "T0"; this.upperbound = t0Upperbound }
            val t1Upperbound = when (t1UpperboundNullable) {
                true -> buildNullableType { innerType = t0 }
                false -> t0
                null -> buildDefinitelyNotNullType { innerType = t0 }
            }
            val t1 = buildTypeParameter { name = "T1"; this.upperbound = t1Upperbound }
            val funcParamType = when (funcParamTypeKind) {
                DNN -> buildDefinitelyNotNullType { innerType = t1 }
                Nullable -> buildNullableType { innerType = t1 }
                NotNull -> t1
                Platform -> buildPlatformType { innerType = t1 }
            }
            val classA = buildClassDeclaration {
                name = "A"
                classKind = ClassKind.OPEN
                typeParameters += t0
                typeParameters += t1
            }
            val func = buildFunctionDeclaration {
                name = "func"
                parameterList = buildParameterList()
                parameterList.parameters.add(buildParameter {
                    name = "t"
                    type = funcParamType
                })
                printNullableAnnotations = true
            }
            classA.functions.add(func)
            val resultA = printer.print(classA)
            assertEquals(expectClassA, resultA)

            val typeArg0Array = arrayOf(
                IrAny,
                buildNullableType { innerType = IrAny }, buildPlatformType { innerType = IrAny }
            )
            val typeArg1Array = arrayOf(
                IrAny,
                buildNullableType { innerType = IrAny }, buildPlatformType { innerType = IrAny }
            )
            for (i0 in 0..2) {
                for (i1 in 0..2) {
                    val expectB = expectClassB[i0][i1] ?: continue
                    val typeArg0 = typeArg0Array[i0]
                    val typeArg1 = typeArg1Array[i1]
                    val classB = buildClassDeclaration {
                        name = "B"
                        classKind = ClassKind.FINAL
                        superType = classA.type.apply {
                            this as IrParameterizedClassifier
                            putTypeArgument(t0, typeArg0)
                            putTypeArgument(t1, typeArg1)
                        }
                        allSuperTypeArguments = HashMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>().apply {
                            put(IrTypeParameterName(t0.name), t0 to typeArg0)
                            put(IrTypeParameterName(t1.name), t1 to typeArg1)
                        }
                    }

                    val funcInB = buildFunctionDeclaration {
                        name = "func"
                        parameterList = buildParameterList()
                        parameterList.parameters.add(buildParameter {
                            name = "t"
                            type = funcParamType
                        })
                        isOverride = true
                        printNullableAnnotations = true
                    }
                    classB.functions.add(funcInB)
                    val resultB = printer.print(classB)
                    assertEquals(
                        NULLABILITY_ANNOTATION_IMPORTS + expectB,
                        resultB,
                        "failed.\n" +
                                "class A:\n" +
                                resultA +
                                "typeArg0 is ${typeArg0.render()}, typeArg1 is ${typeArg1.render()}\n"
                    )
                }
            }
        }

        //<editor-fold desc="Template1 Nullable Parameter">
        @Test
        fun testTemplate1Upperbound0() {
            val expectA = "public open class A<T0 : Any?, T1 : T0?> {\n" +
                    "    abstract open fun func(t: T1?): Unit\n" +
                    "}\n"
            assertTemplate1(
                t0UpperboundNullable = true,
                t1UpperboundNullable = true,
                Nullable,
                expectA,
                arrayOf(
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        // compile error source code
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    )
                )
            )
        }
        @Test
        fun testTemplate1Upperbound1() {
            val expectA = "public open class A<T0 : Any?, T1 : T0> {\n" +
                    "    abstract open fun func(t: T1?): Unit\n" +
                    "}\n"
            assertTemplate1(
                t0UpperboundNullable = true,
                t1UpperboundNullable = false,
                Nullable,
                expectA,
                arrayOf(
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        // compile error source code
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    )
                )
            )
        }
        @Test
        fun testTemplate1Upperbound2() {
            val expectA = "public open class A<T0 : Any?, T1 : T0 & Any> {\n" +
                    "    abstract open fun func(t: T1?): Unit\n" +
                    "}\n"
            assertTemplate1(
                t0UpperboundNullable = true,
                t1UpperboundNullable = null,
                Nullable,
                expectA,
                arrayOf(
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        // compile error source code
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    )
                )
            )
        }
        @Test
        fun testTemplate1Upperbound3() {
            val expectA = "public open class A<T0 : Any, T1 : T0?> {\n" +
                    "    abstract open fun func(t: T1?): Unit\n" +
                    "}\n"
            assertTemplate1(
                t0UpperboundNullable = false,
                t1UpperboundNullable = true,
                Nullable,
                expectA,
                arrayOf(
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        // compile error source code
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    )
                )
            )
        }
        @Test
        fun testTemplate1Upperbound4() {
            val expectA = "public open class A<T0 : Any, T1 : T0> {\n" +
                    "    abstract open fun func(t: T1?): Unit\n" +
                    "}\n"
            assertTemplate1(
                t0UpperboundNullable = false,
                t1UpperboundNullable = false,
                Nullable,
                expectA,
                arrayOf(
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        // compile error source code
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                    )
                )
            )
        }
        //</editor-fold>

        //<editor-fold desc="Template1 Nullable Parameter">
        @Test
        fun testTemplate1Upperbound5() {
            val expectA = "public open class A<T0 : Any?, T1 : T0?> {\n" +
                    "    abstract open fun func(t: T1): Unit\n" +
                    "}\n"
            assertTemplate1(
                t0UpperboundNullable = true,
                t1UpperboundNullable = true,
                NotNull,
                expectA,
                arrayOf(
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any): Unit
                        |}
                        |
                        """.trimMargin(),
                        // compile error source code
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any): Unit
                        |}
                        |
                        """.trimMargin(),
                    )
                )
            )
        }

        @Test
        fun testTemplate1Upperbound6() {
            val expectA = "public open class A<T0 : Any?, T1 : T0> {\n" +
                    "    abstract open fun func(t: T1): Unit\n" +
                    "}\n"
            assertTemplate1(
                t0UpperboundNullable = true,
                t1UpperboundNullable = false,
                NotNull,
                expectA,
                arrayOf(
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any): Unit
                        |}
                        |
                        """.trimMargin(),
                        // compile error source code
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any?, Any>() {
                        |    abstract override open fun func(t: Any): Unit
                        |}
                        |
                        """.trimMargin(),
                    ),
                    arrayOf(
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any?>() {
                        |    abstract override open fun func(t: Any?): Unit
                        |}
                        |
                        """.trimMargin(),
                        """
                        |public class B : A<Any, Any>() {
                        |    abstract override open fun func(t: Any): Unit
                        |}
                        |
                        """.trimMargin(),
                    )
                )
            )
        }
        // todo finish other template1
        //</editor-fold>
        //</editor-fold>
    }
//
//    //<editor-fold desc="Property">
//    @Test
//    fun testPrintSimpleProperty() {
//        val printer = KtIrClassPrinter()
//        val clazzName = "SimpleClassWithSimpleFunction"
//        val propertyName = "simple"
//        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
//        val property = IrPropertyDeclaration(propertyName, clazz).apply {
//            isFinal = true
//            type = IrAny
//            readonly = true
//        }
//        clazz.properties.add(property)
//        val result = printer.print(clazz)
//        val expect = "public class $clazzName {\n" +
//                "    val $propertyName: Any = $todoPropertyInitExpr\n" +
//                "}\n"
//        assertEquals(expect, result)
//    }
//    //</editor-fold>
//
//    //<editor-fold desc="Expression">
//    @Test
//    fun testPrintNewExpression() {
//        val printer = KtIrClassPrinter()
//        val clazzName = "SimpleClassWithSimpleFunction"
//        val funcName = "simple"
//        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
//        val func = IrFunctionDeclaration(funcName, clazz).apply {
//            isFinal = true
//            body = IrBlock().apply {
//                expressions.add(IrNew.create(clazz.type))
//            }
//        }
//        clazz.functions.add(func)
//        val result = printer.print(clazz)
//        val expect = "public class $clazzName {\n" +
//                "    fun $funcName(): Unit {\n" +
//                "        $clazzName()\n" +
//                "    }\n" +
//                "}\n"
//        assertEquals(expect, result)
//    }
//    //</editor-fold>
//
}