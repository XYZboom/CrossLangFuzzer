

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.containers

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.typeParameterContainer]
 */
interface IrTypeParameterContainer : IrElement {
    val typeParameters: List<IrTypeParameter>

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitTypeParameterContainer(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformTypeParameterContainer(this, data) as E

    fun replaceTypeParameters(newTypeParameters: List<IrTypeParameter>)

    fun <D> transformTypeParameters(transformer: IrTransformer<D>, data: D): IrTypeParameterContainer
}
