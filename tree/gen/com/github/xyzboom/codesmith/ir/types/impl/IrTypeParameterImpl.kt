

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.types.impl

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

internal class IrTypeParameterImpl(
    override var name: String,
    override var upperbound: IrType,
) : IrTypeParameter() {
    override val classKind: ClassKind
        get() = upperbound.classKind

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        upperbound.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrTypeParameterImpl {
        transformUpperbound(transformer, data)
        return this
    }

    override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrTypeParameterImpl {
        return this
    }

    override fun <D> transformUpperbound(transformer: IrTransformer<D>, data: D): IrTypeParameterImpl {
        upperbound = upperbound.transform(transformer, data)
        return this
    }

    override fun replaceName(newName: String) {
        name = newName
    }

    override fun replaceUpperbound(newUpperbound: IrType) {
        upperbound = newUpperbound
    }
}
