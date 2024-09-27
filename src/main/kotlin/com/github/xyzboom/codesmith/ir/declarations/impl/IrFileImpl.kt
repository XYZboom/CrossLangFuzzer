package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrFile
import com.github.xyzboom.codesmith.ir.declarations.IrFunction

open class IrFileImpl(
    override val name: String,
) : IrFile {
    override val containingFunctions: MutableList<IrFunction> = ArrayList()
}