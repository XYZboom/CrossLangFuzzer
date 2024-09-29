package com.github.xyzboom.codesmith.ir.declarations.builtin

import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltinTypes

object Function1Class: AbstractBuiltinClass("Function1", IrBuiltinTypes.ANY) {
    override val type: IrConcreteType = IrBuiltinTypes.FUNCTION1
}