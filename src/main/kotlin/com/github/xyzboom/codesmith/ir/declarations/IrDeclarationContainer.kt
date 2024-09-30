package com.github.xyzboom.codesmith.ir.declarations

sealed interface IrDeclarationContainer {
    val declarations: List<IrDeclaration>
}