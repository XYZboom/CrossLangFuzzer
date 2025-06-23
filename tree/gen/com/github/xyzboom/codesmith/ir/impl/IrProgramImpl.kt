

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.impl

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
import com.github.xyzboom.codesmith.ir.visitors.transformInplace

internal class IrProgramImpl(
    override val classes: MutableList<IrClassDeclaration>,
    override val functions: MutableList<IrFunctionDeclaration>,
) : IrProgram() {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        classes.forEach { it.accept(visitor, data) }
        functions.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrProgramImpl {
        classes.transformInplace(transformer, data)
        functions.transformInplace(transformer, data)
        return this
    }
}
