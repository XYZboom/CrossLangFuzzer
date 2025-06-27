

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

internal class IrParameterImpl(
    override var name: String,
    override var type: IrType,
    override var defaultValue: IrExpression?,
) : IrParameter() {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        type.accept(visitor, data)
        defaultValue?.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrParameterImpl {
        transformType(transformer, data)
        transformDefaultValue(transformer, data)
        return this
    }

    override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrParameterImpl {
        return this
    }

    override fun <D> transformType(transformer: IrTransformer<D>, data: D): IrParameterImpl {
        type = type.transform(transformer, data)
        return this
    }

    override fun <D> transformDefaultValue(transformer: IrTransformer<D>, data: D): IrParameterImpl {
        defaultValue = defaultValue?.transform(transformer, data)
        return this
    }
}
