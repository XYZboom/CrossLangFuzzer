package com.github.xyzboom.codesmith.ir_old.types.builtin

import com.github.xyzboom.codesmith.ir_old.types.IrClassType

object IrAny: IrBuiltInType() {
    override val classType: IrClassType = IrClassType.OPEN
}