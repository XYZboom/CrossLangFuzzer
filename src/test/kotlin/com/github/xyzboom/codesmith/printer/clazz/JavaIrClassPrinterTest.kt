package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.TypeKind
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
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
import com.github.xyzboom.codesmith.printer.clazz.JavaIrClassPrinter.Companion.NULLABILITY_ANNOTATION_IMPORTS
import com.github.xyzboom.codesmith.ir.TypeKind.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class JavaIrClassPrinterTest {
    companion object {
        private val todoFunctionBody = "${" ".repeat(8)}throw new RuntimeException();\n"
    }

    @Test
    fun testPrintSimpleClassWithSimpleFunction() {
        val printer = JavaIrClassPrinter()
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
        val expect = NULLABILITY_ANNOTATION_IMPORTS +
                "public final class $clazzName {\n" +
                "    public final void $funcName() {\n" +
                todoFunctionBody +
                "    }\n" +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintSimpleClassWithSimpleStubFunction() {
        val printer = JavaIrClassPrinter()
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
        val expect = NULLABILITY_ANNOTATION_IMPORTS +
                "public final class $clazzName {\n" +
                "    // stub\n" +
                "    /*\n" +
                "    @Override\n" +
                "    public final void $funcName() {\n" +
                todoFunctionBody +
                "    }\n" +
                "    */\n" +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintSimpleClassWithFunctionHasParameter() {
        val printer = JavaIrClassPrinter()
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
        // We now stipulate that the top-level Java classes are not allowed to use platform types
        // todo: we may allow platform type in IR, not late in printer
        val expect = NULLABILITY_ANNOTATION_IMPORTS +
                "public final class $clazzName {\n" +
                "    public final void $funcName(@NotNull Object arg0, @NotNull $clazzName arg1) {\n" +
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
        val printer = JavaIrClassPrinter()
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
            override.add(funcInA)
        }
        classB.functions.add(funcInB)
        val result = printer.print(classB)
        val expect = NULLABILITY_ANNOTATION_IMPORTS +
                "public final class B extends A<@NotNull B>  {\n" +
                "    public abstract void func(@NotNull B t);\n" +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintNullableTypeArg0() {
        /**
         * ```kt
         * open class A<T>
         * class B {
         *     fun func(a: A<B?>) {}
         * }
         * ```
         */
        val printer = JavaIrClassPrinter()
        val t = buildTypeParameter { name = "T"; upperbound = IrAny }
        val classA = buildClassDeclaration {
            name = "A"
            classKind = ClassKind.OPEN
            typeParameters += t
        }
        val classB = buildClassDeclaration {
            name = "B"
            classKind = ClassKind.FINAL
        }
        val funcInB = buildFunctionDeclaration {
            name = "func"
            parameterList = buildParameterList()
            parameterList.parameters.add(buildParameter {
                name = "a"
                type = classA.type.apply {
                    this as IrParameterizedClassifier
                    putTypeArgument(t, buildNullableType { innerType = classB.type })
                }
            })
            printNullableAnnotations = true
        }
        classB.functions.add(funcInB)
        val result = printer.print(classB)
        val expect = NULLABILITY_ANNOTATION_IMPORTS +
                "public final class B {\n" +
                "    public abstract void func(@NotNull A<@Nullable B> a);\n" +
                "}\n"
        assertEquals(expect, result)
    }

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
        val printer = JavaIrClassPrinter()
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
        val expect = NULLABILITY_ANNOTATION_IMPORTS +
                "public final class B implements I<@Nullable Object> {\n" +
                "    @Override\n" +
                "    public @NotNull A<@Nullable Object> func() {\n" +
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
        val printer = JavaIrClassPrinter()
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
        val expect = NULLABILITY_ANNOTATION_IMPORTS +
                "public final class B implements I<@Nullable Object> {\n" +
                "    @Override\n" +
                "    public void func(@NotNull A<@Nullable Object> a) {\n" +
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
            val printer = JavaIrClassPrinter()
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

        @Test
        fun testUpperbound0() {
            /**
             * ```kt
             * open class A<T> {
             *     abstract fun func(t: T)
             * }
             * ```
             */
            val expectA = NULLABILITY_ANNOTATION_IMPORTS +
                    "public class A<T extends @Nullable Object> {\n" +
                    "    public abstract void func(T t);\n" +
                    "}\n"
            val expectAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<@NotNull Object>  {\n" +
                    "    public abstract void func(@NotNull Object t);\n" +
                    "}\n"
            val expectNullableAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<@Nullable Object>  {\n" +
                    "    public abstract void func(@Nullable Object t);\n" +
                    "}\n"
            val expectPlatformAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<Object>  {\n" +
                    "    public abstract void func(Object t);\n" +
                    "}\n"
            assertTemplate0(
                typeParameterUpperboundNullable = true,
                funcParamNullable = NotNull,
                expectA,
                expectAnyB,
                expectNullableAnyB,
                expectPlatformAnyB
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
            val expectA = NULLABILITY_ANNOTATION_IMPORTS +
                    "public class A<T extends @NotNull Object> {\n" +
                    "    public abstract void func(@NotNull T t);\n" +
                    "}\n"
            val expectAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<@NotNull Object>  {\n" +
                    "    public abstract void func(@NotNull Object t);\n" +
                    "}\n"
            val expectPlatformAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<Object>  {\n" +
                    "    public abstract void func(Object t);\n" +
                    "}\n"
            assertTemplate0(
                typeParameterUpperboundNullable = false,
                funcParamNullable = NotNull,
                expectA,
                expectAnyB,
                expectClassBWithTypeArgPlatformAny = expectPlatformAnyB
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
            val expectA = NULLABILITY_ANNOTATION_IMPORTS +
                    "public class A<T extends @Nullable Object> {\n" +
                    "    public abstract void func(@Nullable T t);\n" +
                    "}\n"
            val expectAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<@NotNull Object>  {\n" +
                    "    public abstract void func(@Nullable Object t);\n" +
                    "}\n"
            val expectNullableAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<@Nullable Object>  {\n" +
                    "    public abstract void func(@Nullable Object t);\n" +
                    "}\n"
            val expectPlatformAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<Object>  {\n" +
                    "    public abstract void func(@Nullable Object t);\n" +
                    "}\n"
            assertTemplate0(
                typeParameterUpperboundNullable = true,
                funcParamNullable = Nullable,
                expectA,
                expectAnyB,
                expectNullableAnyB,
                expectPlatformAnyB
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
            val expectA = NULLABILITY_ANNOTATION_IMPORTS +
                    "public class A<T extends @NotNull Object> {\n" +
                    "    public abstract void func(@Nullable T t);\n" +
                    "}\n"
            val expectAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<@NotNull Object>  {\n" +
                    "    public abstract void func(@Nullable Object t);\n" +
                    "}\n"
            val expectPlatformAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<Object>  {\n" +
                    "    public abstract void func(@Nullable Object t);\n" +
                    "}\n"
            assertTemplate0(
                typeParameterUpperboundNullable = false,
                funcParamNullable = Nullable,
                expectA,
                expectAnyB,
                expectClassBWithTypeArgPlatformAny = expectPlatformAnyB
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
            val expectA = NULLABILITY_ANNOTATION_IMPORTS +
                    "public class A<T extends @Nullable Object> {\n" +
                    "    public abstract void func(@NotNull T t);\n" +
                    "}\n"
            val expectAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<@NotNull Object>  {\n" +
                    "    public abstract void func(@NotNull Object t);\n" +
                    "}\n"
            val expectNullableAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<@Nullable Object>  {\n" +
                    "    public abstract void func(@NotNull Object t);\n" +
                    "}\n"
            val expectPlatformAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<Object>  {\n" +
                    "    public abstract void func(@NotNull Object t);\n" +
                    "}\n"
            assertTemplate0(
                typeParameterUpperboundNullable = true,
                funcParamNullable = DNN,
                expectA,
                expectAnyB,
                expectNullableAnyB,
                expectPlatformAnyB
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
            val expectA = NULLABILITY_ANNOTATION_IMPORTS +
                    "public class A<T extends @Nullable Object> {\n" +
                    "    public abstract void func(T t);\n" +
                    "}\n"
            val expectAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<@NotNull Object>  {\n" +
                    "    public abstract void func(@NotNull Object t);\n" +
                    "}\n"
            val expectNullableAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<@Nullable Object>  {\n" +
                    "    public abstract void func(Object t);\n" +
                    "}\n"
            val expectPlatformAnyB = NULLABILITY_ANNOTATION_IMPORTS +
                    "public final class B extends A<Object>  {\n" +
                    "    public abstract void func(Object t);\n" +
                    "}\n"
            assertTemplate0(
                typeParameterUpperboundNullable = true,
                funcParamNullable = Platform,
                expectA,
                expectAnyB,
                expectNullableAnyB,
                expectPlatformAnyB
            )
        }
        //</editor-fold>

        //<editor-fold desc="Template1">
        /**
         * ```kt
         * open class A</*type 1*/> {
         *     abstract fun func(t: /*type 2*/)
         * }
         *
         * open class B</*type 3*/> : A</*type 3*/> {
         *     override fun func(t: /*type 3*/)
         * }
         * ```
         */
        private fun assertTemplate1(
            typeParameterUpperboundNullable: Boolean,
            funcParamNullable: TypeKind,
            expectClassA: String,
            expectClassBWithTypeArgAny: String,
            expectClassBWithTypeArgNullableAny: String? = null
        ) {
            val printer = JavaIrClassPrinter()
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
                 *     override fun func(t: Any)
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
                 *     override fun func(t: Any?)
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
        }
        //</editor-fold>
    }
//
//    //<editor-fold desc="Property">
//    @Test
//    fun testPrintSimpleProperty() {
//        val printer = JavaIrClassPrinter()
//        val clazzName = "SimpleClassWithSimpleFunction"
//        val propertyTypeName = "PType"
//        val propertyName = "simple"
//        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
//        val pClass = IrClassDeclaration(propertyTypeName, IrClassType.FINAL)
//        val property = IrPropertyDeclaration(propertyName, clazz).apply {
//            isFinal = true
//            type = pClass.type
//            readonly = true
//        }
//        clazz.properties.add(property)
//        val result = printer.print(clazz)
//        val expect = NULLABILITY_ANNOTATION_IMPORTS +
//                "public final class $clazzName {\n" +
//                "    public final /*@NotNull*/ $propertyTypeName " +
//                "get${propertyName.replaceFirstChar { it.uppercaseChar() }}() {\n" +
//                todoFunctionBody +
//                "    }\n" +
//                "}\n"
//        assertEquals(expect, result)
//    }
//    //</editor-fold>
//
//    //<editor-fold desc="Expression">
//    @Test
//    fun testPrintNewExpression() {
//        val printer = JavaIrClassPrinter()
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
//        val expect = NULLABILITY_ANNOTATION_IMPORTS +
//                "public final class $clazzName {\n" +
//                "    public final /*@NotNull*/ void $funcName() {\n" +
//                "        new $clazzName();\n" +
//                "    }\n" +
//                "}\n"
//        assertEquals(expect, result)
//    }
//    //</editor-fold>
}