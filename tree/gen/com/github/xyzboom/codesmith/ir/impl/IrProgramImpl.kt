

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
        transformClasses(transformer, data)
        transformFunctions(transformer, data)
        return this
    }

    override fun <D> transformClasses(transformer: IrTransformer<D>, data: D): IrProgramImpl {
        classes.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformFunctions(transformer: IrTransformer<D>, data: D): IrProgramImpl {
        functions.transformInplace(transformer, data)
        return this
    }

    override fun replaceClasses(newClasses: List<IrClassDeclaration>) {
        if (classes === newClasses) return
        classes.clear()
        classes.addAll(newClasses)
    }

    override fun replaceFunctions(newFunctions: List<IrFunctionDeclaration>) {
        if (functions === newFunctions) return
        functions.clear()
        functions.addAll(newFunctions)
    }
}
