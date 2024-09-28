package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrFunction

class IrClassImpl(override val name: String): IrClass {
    override val functions: MutableList<IrFunction> = ArrayList()
    override val classes: MutableList<IrClass> = ArrayList()
}