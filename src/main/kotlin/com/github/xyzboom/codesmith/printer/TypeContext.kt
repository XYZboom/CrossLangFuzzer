package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.printer.clazz.JavaIrClassPrinter

/**
 * The context for printer to printer Java types.
 * [IrUnit] in Java is `void` or [Void] which depends on context.
 * todo: refactor [TypeContext] and [JavaIrClassPrinter]
 */
enum class TypeContext {
    /**
     * ```kt
     * class A<T> : B<T>()
     * //             ^ TypeArgument
     * ```
     */
    TypeArgument,
    Parameter,

    /**
     * ```kt
     * class A<T> : B<T>()
     * //      ^ TypeParameterDeclaration
     * ```
     */
    TypeParameterDeclaration,

    /**
     * ```kt
     * fun <T: T1> func() {}
     * //      ^^ FunctionTypeParameterUpperBound
     * ```
     */
    FunctionTypeParameterUpperBound,
    ReturnType,
    // used for JavaPrinter only
    TypeArgumentInReturnType,
    Other
}