package com.github.xyzboom.codesmith.ir.declarations

private fun IrClassDeclaration.render(): String {
    return "class $name"
}

fun IrDeclaration.render(): String {
    return when (this) {
        is IrClassDeclaration -> render()
        else -> "${this::class.simpleName} ${this.name}"
    }
}