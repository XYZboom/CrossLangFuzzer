package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.ir.IrProgram

interface ICompiler {
    fun compile(program: IrProgram): CompileResult
}