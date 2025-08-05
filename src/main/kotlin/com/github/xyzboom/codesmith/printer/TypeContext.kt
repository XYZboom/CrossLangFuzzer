package com.github.xyzboom.codesmith.printer

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