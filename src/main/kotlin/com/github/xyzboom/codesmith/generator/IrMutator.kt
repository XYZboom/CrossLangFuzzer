package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.declarations.IrProgram

interface IrMutator {
    fun mutate(program: IrProgram): Pair<IrProgram, Boolean>
}