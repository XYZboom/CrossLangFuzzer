package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrFile
import com.github.xyzboom.codesmith.ir.declarations.IrModule

class IrModuleImpl(
    override val name: String
) : IrModule {
    override val dependencies: MutableList<IrModule> = ArrayList()
    override val files: MutableList<IrFile> = ArrayList()
}