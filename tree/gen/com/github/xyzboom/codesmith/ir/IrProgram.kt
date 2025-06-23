

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.containers.IrClassContainer
import com.github.xyzboom.codesmith.ir.containers.IrFuncContainer
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.program]
 */
abstract class IrProgram : IrClassContainer(), IrFuncContainer {
    abstract override val classes: List<IrClassDeclaration>
    abstract override val functions: List<IrFunctionDeclaration>

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitProgram(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformProgram(this, data) as E
}
