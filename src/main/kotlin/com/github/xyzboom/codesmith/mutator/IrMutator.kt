package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.ir.declarations.IrProgram

abstract class IrMutator {
    abstract fun mutateKtExposeKtInternal(program: IrProgram): Pair<IrProgram, Boolean>
    abstract fun mutateJavaExposeKtInternal(program: IrProgram): Pair<IrProgram, Boolean>

    /**
     * do a mutated on specified [program].
     * Return the result program and the mutated position in [MutatorConfig].
     */
    abstract fun mutate(program: IrProgram): Pair<IrProgram, MutatorConfig>
}