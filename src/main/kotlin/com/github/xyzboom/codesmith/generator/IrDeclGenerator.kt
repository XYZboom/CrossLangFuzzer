package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir_old.IrProgram
import com.github.xyzboom.codesmith.ir_old.container.IrContainer
import com.github.xyzboom.codesmith.ir_old.declarations.*
import com.github.xyzboom.codesmith.ir_old.expressions.IrExpressionContainer
import com.github.xyzboom.codesmith.ir_old.expressions.IrFunctionCall
import com.github.xyzboom.codesmith.ir_old.expressions.IrNew
import com.github.xyzboom.codesmith.ir_old.expressions.constant.IrInt
import com.github.xyzboom.codesmith.ir_old.types.IrClassType
import com.github.xyzboom.codesmith.ir_old.types.IrType
import com.github.xyzboom.codesmith.ir_old.types.IrTypeParameter

interface IrDeclGenerator {
    fun randomName(startsWithUpper: Boolean): String

    fun randomIrInt(): IrInt

    fun genProgram(): IrProgram

    fun genTopLevelClass(context: IrProgram, language: Language): IrClassDeclaration {
        return genClass(context, language = language)
    }

    fun genTopLevelFunction(context: IrProgram, language: Language): IrFunctionDeclaration {
        return genFunction(context, context, inAbstract = false, inIntf = false, null, language = language)
    }

    fun genTopLevelFunction(context: IrProgram, language: Language, returnType: IrType): IrFunctionDeclaration {
        return genFunction(context, context, inAbstract = false, inIntf = false, returnType, language = language)
    }

    fun genTopLevelProperty(context: IrProgram, language: Language): IrPropertyDeclaration {
        return genProperty(context, context, inAbstract = false, inIntf = false, null, language = language)
    }

    fun genClass(
        context: IrContainer,
        name: String = randomName(true),
        language: Language
    ): IrClassDeclaration

    fun genFunction(
        classContainer: IrContainer,
        funcContainer: IrContainer,
        inAbstract: Boolean,
        inIntf: Boolean,
        returnType: IrType?,
        name: String = randomName(false),
        language: Language = Language.KOTLIN
    ): IrFunctionDeclaration

    fun randomClassType(): IrClassType
    fun randomType(
        from: IrContainer,
        classContext: IrClassDeclaration?,
        functionContext: IrFunctionDeclaration?,
        finishTypeArguments: Boolean,
        filter: (IrType) -> Boolean
    ): IrType?

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
        language: Language,
        putAllTypeArguments: Map<IrTypeParameter, IrType>
    )

    fun IrClassDeclaration.genOverrides()
    fun IrClassDeclaration.collectFunctionSignatureMap(): FunctionSignatureMap

    /**
     * @param classContainer the container of the function's class. Must be [IrProgram] if the function is top-level.
     */
    fun genFunctionParameter(
        classContainer: IrContainer,
        classContext: IrClassDeclaration?,
        target: IrFunctionDeclaration,
        name: String = randomName(false)
    ): IrParameter

    fun genFunctionReturnType(
        classContainer: IrContainer,
        classContext: IrClassDeclaration?,
        target: IrFunctionDeclaration
    )

    fun genNewExpression(
        block: IrExpressionContainer,
        functionContext: IrFunctionDeclaration,
        context: IrProgram,
        type: IrType?,
        allowSubType: Boolean
    ): IrNew

    fun genFunctionCall(
        block: IrExpressionContainer,
        functionContext: IrFunctionDeclaration,
        context: IrProgram,
        returnType: IrType?,
        allowSubType: Boolean
    ): IrFunctionCall

    fun genProperty(
        classContainer: IrContainer,
        propContainer: IrContainer,
        inAbstract: Boolean,
        inIntf: Boolean,
        type: IrType?,
        name: String = randomName(false),
        language: Language = Language.KOTLIN
    ): IrPropertyDeclaration

    @Suppress("unused")
    fun shuffleLanguage(prog: IrProgram)
}