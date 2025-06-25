package com.github.xyzboom.codesmith.ir.declarations

fun IrClassDeclaration.render(): String {
    return "class $name"
}