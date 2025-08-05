package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builder.buildParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.type
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