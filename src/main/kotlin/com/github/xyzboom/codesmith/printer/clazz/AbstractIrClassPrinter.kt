package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.getActualTypeFromArguments
import com.github.xyzboom.codesmith.ir.visitors.IrTopDownVisitor
import com.github.xyzboom.codesmith.printer.IrPrinter
import com.github.xyzboom.codesmith.printer.TypeContext
import com.github.xyzboom.codesmith.printer.TypeContext.*
import java.util.*

abstract class AbstractIrClassPrinter(
    var indentCount: Int = 0,
    protected val printStub: Boolean = true
) : IrPrinter<IrClassDeclaration, String>, IrTopDownVisitor<StringBuilder>() {
    val elementStack = Stack<IrElement>()
    open val spaceCountInIndent = 4
    override fun visitElement(element: IrElement, data: StringBuilder) {
        elementStack.push(element)
        super.visitElement(element, data)
        require(elementStack.pop() === element)
    }

    val indent get() = " ".repeat(spaceCountInIndent).repeat(indentCount)

    val IrFunctionDeclaration.topLevel: Boolean
        get() {
            require(elementStack.peek() === this)
            return elementStack.size > 1 && elementStack[1] is IrProgram
        }

    val lastClass: IrClassDeclaration?
        get() {
            for (item in elementStack) {
                if (item is IrClassDeclaration) return item
            }
            return null
        }

    abstract fun printIrClassType(irClassType: ClassKind): String

    abstract fun IrClassDeclaration.printExtendList(superType: IrType?, implList: List<IrType>): String

    abstract fun printTopLevelFunctionsAndProperties(program: IrProgram): String

    fun printType(
        irType: IrType,
        typeContext: TypeContext = TypeContext.Other,
        noNullabilityAnnotation: Boolean = false,
        /**
         * ```java
         * public class A<T extends @NotNull Object> {
         *     public void func(@Nullable T t) {}
         *     //               ^^^^^^^^^
         *     // need to force print. Otherwise a kotlin child will consider `t` as NotNull
         * }
         * ```
         */
        forcePrintNullableAnnotationIfIsTypeParameter: Boolean = false,
    ): String {
        return when (typeContext) {
            Parameter, ReturnType, TypeArgumentInReturnType, FunctionTypeParameterUpperBound -> {
                val lastClass = lastClass
                val replaceTypeArg = if (lastClass != null) {
                    /**
                     * For function level type parameter:
                     * ```kt
                     * interface I<T1> {
                     *     fun <T2: T1> func()
                     * }
                     * class A: I<Any> {
                     *     override fun <T2: T1> func() {}
                     *     //                ^^ need to be replaced by `Any`
                     * }
                     * ```
                     */
                    getActualTypeFromArguments(
                        irType,
                        lastClass.allSuperTypeArguments,
                        typeContext != FunctionTypeParameterUpperBound
                    )
                } else {
                    irType
                }
                printTypeDirectly(
                    replaceTypeArg, typeContext,
                    noNullabilityAnnotation
                )
            }

            else -> printTypeDirectly(irType, typeContext, noNullabilityAnnotation)
        }
    }

    /**
     * @param printNullableAnnotation Print nullability annotation with comment when set to false.
     *          Print full nullability annotation when set to true.
     *          **NOT AVAILABLE** when [noNullabilityAnnotation] set true.
     *          **NOT AVAILABLE** in Kotlin.
     * @param noNullabilityAnnotation Print no nullability annotation of types when set to true.
     *          Suppress [printNullableAnnotation] when [noNullabilityAnnotation] set to true.
     *          **NOT AVAILABLE** in Kotlin.
     */
    abstract fun printTypeDirectly(
        irType: IrType,
        typeContext: TypeContext = TypeContext.Other,
        noNullabilityAnnotation: Boolean = false
    ): String
}