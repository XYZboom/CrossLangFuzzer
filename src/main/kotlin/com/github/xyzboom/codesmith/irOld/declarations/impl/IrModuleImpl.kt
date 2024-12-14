package com.github.xyzboom.codesmith.irOld.declarations.impl

import com.github.xyzboom.codesmith.irOld.declarations.IrModule
import com.github.xyzboom.codesmith.irOld.declarations.IrPackage
import com.github.xyzboom.codesmith.irOld.declarations.IrProgram

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