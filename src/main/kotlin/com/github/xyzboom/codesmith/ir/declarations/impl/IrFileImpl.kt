package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrFile

class IrFileImpl(
    override val name: String,
    override val extension: String
) : IrFile() {
}