package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.ir.IrProgram

object MockCompilerRunner: ICompilerRunner {
    override fun compile(program: IrProgram, compilers: List<ICompiler>): List<CompileResult> {
        throw IllegalStateException("Should not call me in a test.")
    }
}