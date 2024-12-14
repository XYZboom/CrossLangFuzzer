package com.github.xyzboom.codesmith.ir.declarations

class IrClassDeclaration(
    name: String,
    val fields: MutableList<IrFieldDeclaration> = mutableListOf(),
    val functions: MutableList<IrFunctionDeclaration> = mutableListOf(),
): IrDeclaration(name) {
}