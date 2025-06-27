

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.impl

import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
import com.github.xyzboom.codesmith.ir.visitors.transformInplace

internal class IrParameterListImpl(
    override var parameters: MutableList<IrParameter>,
) : IrParameterList() {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        parameters.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrParameterListImpl {
        transformParameters(transformer, data)
        return this
    }

    override fun <D> transformParameters(transformer: IrTransformer<D>, data: D): IrParameterListImpl {
        parameters.transformInplace(transformer, data)
        return this
    }
}
