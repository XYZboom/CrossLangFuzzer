package com.github.xyzboom.codesmith.ir.declarations.builtin

import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.declarations.impl.IrFunctionImpl
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltinTypes

object AnyClass: AbstractBuiltinClass("Any") {
    override val functions: MutableList<IrFunction> =
        arrayListOf(
            IrFunctionImpl("equals", this, BooleanClass.type)
        )
    override val type: IrConcreteType = IrBuiltinTypes.ANY
}