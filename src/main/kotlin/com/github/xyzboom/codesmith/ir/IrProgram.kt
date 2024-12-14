package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration

class IrProgram: IrElement() {
    /**
     * Top-level functions
     */
    val functions = mutableListOf<IrFunctionDeclaration>()

    /**
     * Top-level classes
     */
    val classes = mutableListOf<IrClassDeclaration>()

    /**
     * Properties can be top level. Top-level properties in Java will be renamed to getXXX.
     */
    val properties = mutableListOf<IrPropertyDeclaration>()
}