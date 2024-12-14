package com.github.xyzboom.codesmith.ir.container

import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration

interface IrFunctionContainer {
    /**
     * Top-level functions
     */
    val functions: MutableList<IrFunctionDeclaration>

}