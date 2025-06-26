

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.namedElement]
 */
interface IrNamedElement : IrElement {
    val name: String

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitNamedElement(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformNamedElement(this, data) as E

    fun replaceName(newName: String)

    fun <D> transformName(transformer: IrTransformer<D>, data: D): IrNamedElement
}
