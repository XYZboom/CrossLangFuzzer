

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.parameterList]
 */
abstract class IrParameterList : IrElement {
    abstract val parameters: List<IrParameter>

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitParameterList(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformParameterList(this, data) as E

    abstract fun replaceParameters(newParameters: List<IrParameter>)

    abstract fun <D> transformParameters(transformer: IrTransformer<D>, data: D): IrParameterList
}
