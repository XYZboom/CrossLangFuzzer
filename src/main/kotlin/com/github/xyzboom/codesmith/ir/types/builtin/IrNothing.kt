package com.github.xyzboom.codesmith.ir.types.builtin

import com.github.xyzboom.codesmith.ir.types.IrClassType

object IrNothing: IrBuiltInType() {
    override val classType: IrClassType = IrClassType.FINAL
}