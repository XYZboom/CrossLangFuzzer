package com.github.xyzboom.codesmith.ir.types.builtin

import com.github.xyzboom.codesmith.ir.types.IrType

sealed class IrBuiltInType: IrType() {
    final override fun equalsIgnoreTypeArguments(other: IrType): Boolean {
        return other === this
    }

    override fun toString(): String {
        return this::class.simpleName!!
    }
}