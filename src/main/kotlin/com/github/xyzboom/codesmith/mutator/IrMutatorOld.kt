package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.ir_old.IrProgram

abstract class IrMutatorOld {
    abstract fun mutate(program: IrProgram): Boolean
}