

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.containers.builder

import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.containers.IrFuncContainer
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration

@BuilderDsl
interface IrFuncContainerBuilder {
    abstract var name: String
    abstract val functions: MutableList<IrFunctionDeclaration>

    fun build(): IrFuncContainer
}
