

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.parameter]
 */
abstract class IrParameter : IrElement {
    abstract var name: String
    abstract var type: IrType
    abstract var defaultValue: IrExpression?

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitParameter(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformParameter(this, data) as E

    abstract fun <D> transformName(transformer: IrTransformer<D>, data: D): IrParameter

    abstract fun <D> transformType(transformer: IrTransformer<D>, data: D): IrParameter

    abstract fun <D> transformDefaultValue(transformer: IrTransformer<D>, data: D): IrParameter
}
