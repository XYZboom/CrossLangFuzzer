package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrFile
import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.declarations.IrModule

open class IrFileImpl(
    override val name: String,
    override val containingModule: IrModule
): IrFile {
    override val functions: MutableList<IrFunction> = ArrayList()
    override val classes: MutableList<IrClass> = ArrayList()
}