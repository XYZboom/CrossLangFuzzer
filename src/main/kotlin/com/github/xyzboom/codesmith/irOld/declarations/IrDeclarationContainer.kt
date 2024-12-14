package com.github.xyzboom.codesmith.irOld.declarations

sealed interface IrDeclarationContainer {
    val declarations: List<IrDeclaration>
}