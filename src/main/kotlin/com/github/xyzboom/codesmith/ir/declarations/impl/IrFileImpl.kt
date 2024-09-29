package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.*

open class IrFileImpl(
    override val name: String,
    override val containingPackage: IrPackage
): IrFile {
    override val functions: MutableList<IrFunction> = ArrayList()
    override val classes: MutableList<IrClass> = ArrayList()

    companion object {
        @JvmStatic
        val builtin = IrFileImpl("<built-in>", IrPackageImpl.builtin)
    }
}