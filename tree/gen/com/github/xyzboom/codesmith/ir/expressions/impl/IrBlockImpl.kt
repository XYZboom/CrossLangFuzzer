

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.expressions.impl

import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
import com.github.xyzboom.codesmith.ir.visitors.transformInplace

internal class IrBlockImpl(
    override val expressions: MutableList<IrExpression>,
) : IrBlock() {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        expressions.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrBlockImpl {
        transformExpressions(transformer, data)
        return this
    }

    override fun <D> transformExpressions(transformer: IrTransformer<D>, data: D): IrBlockImpl {
        expressions.transformInplace(transformer, data)
        return this
    }

    override fun replaceExpressions(newExpressions: List<IrExpression>) {
        if (expressions === newExpressions) return
        expressions.clear()
        expressions.addAll(newExpressions)
    }
}
