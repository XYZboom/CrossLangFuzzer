package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrModule
import com.github.xyzboom.codesmith.ir.declarations.IrPackage
import com.github.xyzboom.codesmith.ir.declarations.IrProgram

class IrModuleImpl(
    override val name: String,
    override val program: IrProgram
): IrModule {
    override val dependencies: MutableList<IrModule> = ArrayList()
    override val packages: MutableList<IrPackage> = ArrayList()

    companion object {
        @JvmStatic
        val builtin = IrModuleImpl("<built-in>", IrProgramImpl.builtin)
    }
}