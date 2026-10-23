package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName

/**
 * Renders the [DefaultValue] of a type into the syntax of the target language.
 *
 * The decision about *what* the value is belongs to [DefaultValueSupport]; this interface only
 * turns that decision into source code, so the Kotlin and the Java printer share the semantics
 * and differ only in the syntax.
 */
interface DefaultValueRenderer {
    /**
     * The expression that produces a value of [type].
     *
     * @param owner the class that declares the function whose return type or parameter type is
     *        [type], used to resolve the type parameters of that class.
     * @param function the function that uses [type], used to resolve its own type parameters.
     * @param depth the current nesting depth of anonymous subclasses.
     */
    fun renderValue(
        type: IrType,
        owner: IrClassDeclaration?,
        function: IrFunctionDeclaration?,
        depth: Int,
    ): String

    /**
     * The statement that returns a value of [type] from a function body.
     *
     * @param depth the current nesting depth of anonymous subclasses.
     */
    fun renderReturnStatement(
        type: IrType,
        owner: IrClassDeclaration?,
        function: IrFunctionDeclaration?,
        depth: Int,
    ): String

    /**
     * The type arguments of a constructor call or of an anonymous supertype, rendered in the
     * target language.
     */
    fun renderTypeArguments(typeArguments: List<IrType>): String

    /**
     * The name of the exception that is thrown when no value can be produced, in the target
     * language.
     */
    val unavailableExceptionExpression: String

    /**
     * The substitution of the type parameters of [owner] and [function] by their upper bounds.
     */
    fun substitution(
        owner: IrClassDeclaration?,
        function: IrFunctionDeclaration?,
    ): Map<IrTypeParameterName, com.github.xyzboom.codesmith.ir.types.IrType> =
        DefaultValueSupport.substitutionFor(owner, function)
            .mapValues { (_, pair) -> pair.second }
}
