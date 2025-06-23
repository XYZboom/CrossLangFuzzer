package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.ir_old.IrProgram

abstract class IrMutator {
    abstract fun mutate(program: IrProgram): Boolean
}