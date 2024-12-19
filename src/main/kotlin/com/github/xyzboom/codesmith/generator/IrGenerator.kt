package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.container.IrContainer
import com.github.xyzboom.codesmith.ir.declarations.FunctionSignatureMap
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.expressions.constant.IrInt
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrType

interface IrGenerator {
    fun randomName(startsWithUpper: Boolean): String

    fun randomIrInt(): IrInt

    fun genProgram(): IrProgram

    fun genClass(context: IrContainer, name: String = randomName(true)): IrClassDeclaration
    fun genFunction(
        classContainer: IrContainer,
        funcContainer: IrContainer,
        inAbstract: Boolean,
        inIntf: Boolean,
        name: String = randomName(false),
        language: Language = Language.KOTLIN
    ): IrFunctionDeclaration

    fun randomClassType(): IrClassType
    fun randomType(from: IrContainer, filter: (IrClassDeclaration) -> Boolean): IrType?
    fun IrClassDeclaration.genSuperTypes(context: IrContainer)

    /**
     * Generate an override function.
     * @param makeAbstract true if override but still abstract
     * @param isStub true if generate an override stub for [IrClassDeclaration],
     *               no source will be print for this function
     */
    fun IrClassDeclaration.genOverrideFunction(
        from: List<IrFunctionDeclaration>,
        makeAbstract: Boolean,
        isStub: Boolean,
        isFinal: Boolean?,
        language: Language
    )

    fun IrClassDeclaration.genOverrides()
    fun IrClassDeclaration.collectFunctionSignatureMap(): FunctionSignatureMap

    /**
     * @param classContainer the container of the function's class. Must be [IrProgram] if the function is top-level.
     */
    fun genFunctionParameter(
        classContainer: IrContainer,
        name: String = randomName(false)
    ): IrParameter
}