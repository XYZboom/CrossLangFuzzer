package com.github.xyzboom.codesmith.ir.types.builtin

import com.github.xyzboom.codesmith.ir.types.IrClassType

object IrAny: IrBuiltInType() {
    override val classType: IrClassType = IrClassType.OPEN
}