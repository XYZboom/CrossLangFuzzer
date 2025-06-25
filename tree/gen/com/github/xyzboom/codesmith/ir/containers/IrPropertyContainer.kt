

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.containers

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.propertyContainer]
 */
interface IrPropertyContainer : IrElement {
    val properties: List<IrPropertyDeclaration>

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitPropertyContainer(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformPropertyContainer(this, data) as E

    fun replaceProperties(newProperties: List<IrPropertyDeclaration>)

    fun <D> transformProperties(transformer: IrTransformer<D>, data: D): IrPropertyContainer
}
