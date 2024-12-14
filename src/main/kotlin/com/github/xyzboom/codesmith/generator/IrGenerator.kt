package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.container.IrClassContainer
import com.github.xyzboom.codesmith.ir.container.IrFunctionContainer
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.expressions.constant.IrInt

interface IrGenerator {
    fun randomName(startsWithUpper: Boolean): String

    fun randomIrInt(): IrInt

    fun genProgram(): IrProgram

    fun genClass(context: IrClassContainer, name: String = randomName(true)): IrClassDeclaration
    fun genFunction(
        context: IrFunctionContainer,
        name: String = randomName(false),
        language: Language = Language.KOTLIN
    ): IrFunctionDeclaration
}