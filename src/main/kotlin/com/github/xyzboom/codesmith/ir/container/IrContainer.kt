package com.github.xyzboom.codesmith.ir.container

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration

interface IrContainer {
    val classes: MutableList<IrClassDeclaration>
    val functions: MutableList<IrFunctionDeclaration>

    var superContainer: IrContainer?
    val allClasses: List<IrClassDeclaration>
        get() = classes + (superContainer?.classes ?: emptyList())
}