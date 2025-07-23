package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.ir.IrProgram

object MockCompilerRunner: ICompilerRunner {
    override fun compile(program: IrProgram): List<CompileResult> {
        throw IllegalStateException("Should not call me in a test.")
    }
}