package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrModule
import com.github.xyzboom.codesmith.ir.declarations.IrProgram

class IrProgramImpl: IrProgram {
    override val modules: MutableList<IrModule> = ArrayList()
}