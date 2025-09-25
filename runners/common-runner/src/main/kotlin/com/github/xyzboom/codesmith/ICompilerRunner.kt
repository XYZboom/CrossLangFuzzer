package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.ir.IrProgram

interface ICompilerRunner {
    /**
     * compile the given [program].
     * returns one element if we are doing normal testing,
     * otherwise differential testing
     */
    fun compile(program: IrProgram, compilers: List<ICompiler>): List<CompileResult> {
        return compilers.map { compiler -> compiler.compile(program) }
    }
}