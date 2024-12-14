package com.github.xyzboom.codesmith.irOld.declarations.impl

import com.github.xyzboom.codesmith.irOld.declarations.IrFile
import com.github.xyzboom.codesmith.irOld.declarations.IrModule
import com.github.xyzboom.codesmith.irOld.declarations.IrPackage

class IrPackageImpl(
    override val name: String,
    override val parent: IrPackage?,
    override val containingModule: IrModule
): IrPackage {
    override val files: MutableList<IrFile> = ArrayList()

    companion object {
        @JvmStatic
        val builtin = IrPackageImpl("<built-in>", null, IrModuleImpl.builtin)
    }
}