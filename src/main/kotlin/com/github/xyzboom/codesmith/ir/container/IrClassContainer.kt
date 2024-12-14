package com.github.xyzboom.codesmith.ir.container

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration

interface IrClassContainer {
    /**
     * Top-level classes
     */
    val classes: MutableList<IrClassDeclaration>
}