

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.containers

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.funcContainer]
 */
interface IrFuncContainer : IrElement {
    var functions: MutableList<IrFunctionDeclaration>

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitFuncContainer(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformFuncContainer(this, data) as E

    fun <D> transformFunctions(transformer: IrTransformer<D>, data: D): IrFuncContainer
}
