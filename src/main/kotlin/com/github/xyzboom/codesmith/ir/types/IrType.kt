package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.IrElement

abstract class IrType: IrElement() {
    abstract val classType: IrClassType
}