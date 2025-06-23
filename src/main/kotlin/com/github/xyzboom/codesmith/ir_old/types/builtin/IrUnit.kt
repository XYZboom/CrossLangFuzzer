package com.github.xyzboom.codesmith.ir_old.types.builtin

import com.github.xyzboom.codesmith.ir_old.types.IrClassType

object IrUnit: IrBuiltInType() {
    override val classType: IrClassType = IrClassType.FINAL
}