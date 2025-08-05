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

class ScalaClassPrinterTest {
    companion object {
        private val todoFunctionBody = "${" ".repeat(4)}???\n"
    }

    @Test
    fun testPrintSimpleClassWithSimpleFunction() {
        val printer = ScalaIrClassPrinter()
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
        val expect = "class $clazzName {\n" +
                "  def $funcName(): Unit = \n" +
                todoFunctionBody +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintSimpleClassWithSimpleStubFunction() {
        val printer = ScalaIrClassPrinter()
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
        val expect = "class $clazzName {\n" +
                "  // stub\n" +
                "  /*\n" +
                "  override def $funcName(): Unit = \n" +
                "    ???\n" +
                "  */\n" +
                "}\n"
        assertEquals(expect, result)
    }

    @Test
    fun testPrintSimpleClassWithFunctionHasParameter() {
        val printer = ScalaIrClassPrinter()
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
        val expect = "class SimpleClassWithFunctionHasParameter {\n" +
                "  def simple(arg0: Object, arg1: SimpleClassWithFunctionHasParameter): Unit = \n" +
                "    ???\n" +
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
        val printer = ScalaIrClassPrinter()
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
        val expect = "class B extends A[B] {\n" +
                "  def func(t: B): Unit\n" +
                "}\n"
        assertEquals(expect, result)
    }
//    //<editor-fold desc="Property">
//    @Test
//    @Disabled
//    fun testPrintSimpleProperty() {
//        val printer = ScalaIrClassPrinter()
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
//        val expect = "public final class $clazzName {\n" +
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
//    @Disabled
//    fun testPrintNewExpression() {
//        val printer = ScalaIrClassPrinter()
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
//        val expect = "public final class $clazzName {\n" +
//                "    public final /*@NotNull*/ void $funcName() {\n" +
//                "        new $clazzName();\n" +
//                "    }\n" +
//                "}\n"
//        assertEquals(expect, result)
//    }
}