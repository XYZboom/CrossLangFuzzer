package com.github.xyzboom.codesmith.ir_old.types.builtin

import com.github.xyzboom.codesmith.ir_old.types.IrClassType

object IrNothing: IrBuiltInType() {
    override val classType: IrClassType = IrClassType.FINAL
}