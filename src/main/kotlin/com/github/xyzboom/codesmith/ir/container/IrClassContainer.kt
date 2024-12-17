package com.github.xyzboom.codesmith.ir.container

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration

interface IrClassContainer {
    /**
     * Top-level classes
     */
    val classes: MutableList<IrClassDeclaration>
    val superContainer: IrClassContainer?
    val allClasses: List<IrClassDeclaration>
        get() = classes + (superContainer?.classes ?: emptyList())
}