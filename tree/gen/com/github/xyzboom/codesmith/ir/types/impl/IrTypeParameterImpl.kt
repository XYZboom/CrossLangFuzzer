

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.types.impl

import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

internal class IrTypeParameterImpl : IrTypeParameter() {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {}

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrTypeParameterImpl {
        return this
    }
}
