

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.types.impl

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

internal class IrNullableTypeImpl(
    override var innerType: IrType,
) : IrNullableType() {
    override val classKind: ClassKind
        get() = innerType.classKind

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        innerType.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrNullableTypeImpl {
        transformInnerType(transformer, data)
        return this
    }

    override fun <D> transformInnerType(transformer: IrTransformer<D>, data: D): IrNullableTypeImpl {
        innerType = innerType.transform(transformer, data)
        return this
    }

    override fun replaceInnerType(newInnerType: IrType) {
        innerType = newInnerType
    }
}
