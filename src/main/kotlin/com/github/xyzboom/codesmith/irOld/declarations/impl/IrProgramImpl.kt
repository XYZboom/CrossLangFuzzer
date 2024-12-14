package com.github.xyzboom.codesmith.irOld.declarations.impl

import com.github.xyzboom.codesmith.irOld.declarations.IrModule
import com.github.xyzboom.codesmith.irOld.declarations.IrProgram

class IrProgramImpl: IrProgram {
    override val modules: MutableList<IrModule> = ArrayList()
    override lateinit var mainModule: IrModule
    override val hasMainModule: Boolean = ::mainModule.isInitialized

    companion object {
        @JvmStatic
        val builtin = IrProgramImpl()
    }
}