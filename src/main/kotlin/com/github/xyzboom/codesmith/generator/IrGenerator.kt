package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.expressions.constant.IrInt

interface IrGenerator {
    fun randomName(startsWithUpper: Boolean): String

    fun randomIrInt(): IrInt

    fun genProgram(): IrProgram

    fun genClass(): IrClassDeclaration
}