package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.ir.IrProgram

abstract class IrMutator {
    abstract fun mutate(program: IrProgram): Boolean
}