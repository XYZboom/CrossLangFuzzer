package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ir.IrProgram

abstract class MinimizeRunner(
    val initProg: IrProgram,
) {
    /**
     * compile the given [program].
     * returns `Pair<CompileResult, null>` using normal testing or
     * `Pair<CompileResult, CompileResult>` using differential testing
     */
    abstract fun compile(program: IrProgram): Pair<CompileResult, CompileResult?>

    fun minimize(): IrProgram {
        val initResult = compile(initProg)
        TODO()
    }
}