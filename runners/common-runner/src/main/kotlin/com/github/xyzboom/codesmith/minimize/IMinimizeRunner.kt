package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ICompilerRunner
import com.github.xyzboom.codesmith.ir.IrProgram

interface IMinimizeRunner: ICompilerRunner {
    fun minimize(
        initProg: IrProgram,
        initCompileResult: List<CompileResult>,
        compilers: List<ICompiler>,
    ): Pair<IrProgram, List<CompileResult>>
}