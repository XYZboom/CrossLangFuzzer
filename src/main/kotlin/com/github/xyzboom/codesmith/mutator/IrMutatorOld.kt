package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.irOld.declarations.IrProgram

abstract class IrMutatorOld {
    abstract fun mutateKtExposeKtInternal(program: IrProgram): Pair<IrProgram, Boolean>
    abstract fun mutateJavaExposeKtInternal(program: IrProgram): Pair<IrProgram, Boolean>
    abstract fun mutateConstructorSuperCallPrivate(program: IrProgram): Pair<IrProgram, Boolean>
    abstract fun mutateConstructorSuperCallInternal(program: IrProgram): Pair<IrProgram, Boolean>
    abstract fun mutateConstructorNormalCallPrivate(program: IrProgram): Pair<IrProgram, Boolean>
    abstract fun mutateConstructorNormalCallInternal(program: IrProgram): Pair<IrProgram, Boolean>
    abstract fun mutateFunctionCallPrivate(program: IrProgram): Pair<IrProgram, Boolean>
    abstract fun mutateFunctionCallInternal(program: IrProgram): Pair<IrProgram, Boolean>

    /**
     * do a mutated on specified [program].
     * Return the result program and the mutated position in [MutatorConfigOld].
     */
    abstract fun mutate(program: IrProgram): Pair<IrProgram, MutatorConfigOld>
}