package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.container.IrClassContainer
import com.github.xyzboom.codesmith.ir.container.IrFunctionContainer
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration

class IrProgram: IrElement(), IrClassContainer, IrFunctionContainer {
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
    val properties = mutableListOf<IrPropertyDeclaration>()

    override val superContainer: IrClassContainer? = null
}