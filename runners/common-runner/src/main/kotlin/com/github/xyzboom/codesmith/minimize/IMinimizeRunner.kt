package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompilerRunner
import com.github.xyzboom.codesmith.ir.IrProgram

interface IMinimizeRunner: ICompilerRunner {
    /**
     * compile the given [program].
     * returns one element if we are doing normal testing,
     * otherwise differential testing
     */
    override fun compile(program: IrProgram): List<CompileResult>

    fun minimize(initProg: IrProgram, initCompileResult: List<CompileResult>): Pair<IrProgram, List<CompileResult>>
}