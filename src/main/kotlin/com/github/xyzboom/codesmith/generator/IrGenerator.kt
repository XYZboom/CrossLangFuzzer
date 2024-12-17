package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.container.IrClassContainer
import com.github.xyzboom.codesmith.ir.container.IrFunctionContainer
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.expressions.constant.IrInt
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrType

interface IrGenerator {
    fun randomName(startsWithUpper: Boolean): String

    fun randomIrInt(): IrInt

    fun genProgram(): IrProgram

    fun genClass(context: IrClassContainer, name: String = randomName(true)): IrClassDeclaration
    fun genFunction(
        context: IrFunctionContainer,
        inAbstract: Boolean,
        inIntf: Boolean,
        name: String = randomName(false),
        language: Language = Language.KOTLIN
    ): IrFunctionDeclaration

    fun randomClassType(): IrClassType
    fun randomType(from: IrClassContainer, filter: (IrClassDeclaration) -> Boolean): IrType?
    fun IrClassDeclaration.genSuperTypes(context: IrClassContainer)

    /**
     * Generate an override function.
     * @param stillAbstract true if override but still abstract
     * @param isStub true if generate an override stub for [IrClassDeclaration],
     *               no source will be print for this function
     */
    fun IrClassDeclaration.genOverrideFunction(
        context: IrFunctionContainer,
        from: IrFunctionDeclaration,
        stillAbstract: Boolean,
        isStub: Boolean,
        language: Language
    )
}