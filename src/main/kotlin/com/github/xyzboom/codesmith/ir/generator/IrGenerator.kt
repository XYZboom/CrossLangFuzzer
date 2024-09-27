package com.github.xyzboom.codesmith.ir.generator

import com.github.xyzboom.codesmith.ir.declarations.IrProgram

interface IrGenerator {
    fun generate(): IrProgram

    fun randomName(startsWithUpper: Boolean): String
}