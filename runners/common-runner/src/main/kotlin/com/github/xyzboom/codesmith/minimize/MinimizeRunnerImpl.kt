package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompilerRunner
import com.github.xyzboom.codesmith.ir.IrProgram

class MinimizeRunnerImpl(
    compilerRunner: ICompilerRunner
): IMinimizeRunner, ICompilerRunner by compilerRunner {
    fun classLevelMinimize(initProg: IrProgram, initResult: List<CompileResult>): Pair<IrProgram, List<CompileResult>> {
        val isDiff = initResult.size > 1
        return ClassLevelMinimizeRunner(this).minimize(initProg, initResult)
    }

    override fun minimize(initProg: IrProgram, initResult: List<CompileResult>): Pair<IrProgram, List<CompileResult>> {
        val classMinimizedProg = classLevelMinimize(initProg, initResult)
        // TODO
        return classMinimizedProg
    }
}