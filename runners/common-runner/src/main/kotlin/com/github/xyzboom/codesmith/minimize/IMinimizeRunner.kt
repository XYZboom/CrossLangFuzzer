package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ir.IrProgram

interface IMinimizeRunner {
    /**
     * compile the given [program].
     * returns `Pair<CompileResult, null>` using normal testing or
     * `Pair<CompileResult, CompileResult>` using differential testing
     */
    fun compile(program: IrProgram): Pair<CompileResult, CompileResult?>

    fun minimize(initProg: IrProgram): IrProgram {
        val initResult = compile(initProg)
        TODO()
    }
}