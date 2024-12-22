package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.container.IrContainer
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration

class IrProgram: IrElement(), IrContainer {
    /**
     * Top-level functions
     */
    override val functions = mutableListOf<IrFunctionDeclaration>()

    /**
     * Top-level classes
     */
    override val classes = mutableListOf<IrClassDeclaration>()

    /**
     * Properties can be top level. Top-level properties in Java will be renamed to getXXX.
     */
    override val properties = mutableListOf<IrPropertyDeclaration>()

    override var superContainer: IrContainer? = null
}