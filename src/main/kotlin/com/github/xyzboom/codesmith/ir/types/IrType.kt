package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.IrElement

abstract class IrType : IrElement() {
    abstract val classType: IrClassType
    open val unfinished: Boolean get() = false
    abstract fun equalsIgnoreTypeArguments(other: IrType): Boolean
    open fun copyForOverride(): IrType = this
}