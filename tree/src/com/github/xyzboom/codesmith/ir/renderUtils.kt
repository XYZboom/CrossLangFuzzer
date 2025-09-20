package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.declarations.IrDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.declarations.render
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.render

fun IrElement.render(): String {
    return when (this) {
        is IrType -> render()
        is IrDeclaration -> render()
        is IrParameter -> render()
        // todo
        else -> toString()
    }
}