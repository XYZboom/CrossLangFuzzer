

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.containers

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrPureAbstractElement
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.expressionContainer]
 */
abstract class IrExpressionContainer : IrPureAbstractElement(), IrElement {
    abstract var expressions: MutableList<IrExpression>

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitExpressionContainer(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformExpressionContainer(this, data) as E

    abstract fun <D> transformExpressions(transformer: IrTransformer<D>, data: D): IrExpressionContainer
}
