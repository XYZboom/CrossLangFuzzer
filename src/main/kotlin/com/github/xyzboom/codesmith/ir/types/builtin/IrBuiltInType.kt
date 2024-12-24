package com.github.xyzboom.codesmith.ir.types.builtin

import com.github.xyzboom.codesmith.ir.types.IrType

sealed class IrBuiltInType: IrType() {
    companion object {
        val ALL_BUILTINS = listOf(
            IrAny,
            IrNothing,
            IrUnit
        )
    }

    final override fun equalsIgnoreTypeArguments(other: IrType): Boolean {
        return other === this
    }
}