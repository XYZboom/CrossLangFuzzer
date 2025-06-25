

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.expressions

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.containers.IrExpressionContainer
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.block]
 */
abstract class IrBlock : IrExpressionContainer() {
    abstract override val expressions: List<IrExpression>

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitBlock(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformBlock(this, data) as E

    abstract override fun replaceExpressions(newExpressions: List<IrExpression>)

    abstract override fun <D> transformExpressions(transformer: IrTransformer<D>, data: D): IrBlock
}
