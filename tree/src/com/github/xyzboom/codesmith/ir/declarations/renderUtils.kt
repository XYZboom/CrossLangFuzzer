package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.types.render

private fun IrClassDeclaration.render(): String {
    return "class $name"
}

fun IrDeclaration.render(): String {
    return when (this) {
        is IrClassDeclaration -> render()
        else -> "${this::class.simpleName} ${this.name}"
    }
}

fun IrParameter.render(): String {
    return "${name}: ${type.render()}"
}